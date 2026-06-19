package me.xjqsh.lrtactical.item;

import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.item.GunTooltipPart;
import me.xjqsh.lrtactical.api.event.ConsumableUseEvent;
import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.capability.CombatPropertiesProvider;
import me.xjqsh.lrtactical.capability.CustomItemCoolDownsProvider;
import me.xjqsh.lrtactical.client.renderer.item.ConsumableItemRenderer;
import me.xjqsh.lrtactical.item.consumable.ConsumableData;
import me.xjqsh.lrtactical.item.index.ConsumableIndex;
import me.xjqsh.lrtactical.util.TooltipHideFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.Consumer;

public class ConsumableItem extends Item implements IAnimationItem, IConsumable {
    private static final Style TACZ_STAT_LABEL_STYLE = Style.EMPTY.withColor(0x777777);

    public ConsumableItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public @Nullable FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        return this.getConsumableIndex(stack).map(ConsumableIndex::getFoodProperties).orElse(null);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.getConsumableIndex(stack).map(ConsumableIndex::getMaxStackSize).orElse(1);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return this.getConsumableIndex(stack)
                .map(index -> index.getData().getMaxDurability())
                .orElse(0);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return this.getMaxDamage(stack) > 0;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return this.getConsumableIndex(stack).map(index -> {
            if (index.getData().isToggleUse()) {
                return 72000;
            }
            return index.getData().getUseDuration();
        }).orElse(0);
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @NotNull
    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.EMPTY;
    }

    @Override
    public int getDrawTime(ItemStack stack) {
        return this.getConsumableIndex(stack).map(index -> index.getData().getDrawTime()).orElse(0);
    }

    @Override
    public int getPutAwayTime(ItemStack stack) {
        return this.getConsumableIndex(stack).map(index -> index.getData().getPutAwayTime()).orElse(0);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private ConsumableItemRenderer renderer = null;

            @Override
            public ConsumableItemRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new ConsumableItemRenderer();
                }
                return renderer;
            }
        });
    }

    @NotNull
    @Override
    public String getDescriptionId(@NotNull ItemStack stack) {
        return this.getConsumableIndex(stack).map(ConsumableIndex::getDescriptionId).orElse(super.getDescriptionId(stack));
    }

    @ParametersAreNonnullByDefault
    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (usedHand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.fail(player.getItemInHand(usedHand));
        }

        ItemStack stack = player.getItemInHand(usedHand);
        boolean drawing = player.getCapability(CombatPropertiesProvider.CAPABILITY)
                .map(cap -> cap.getCoolDownTick() > 0)
                .orElse(false);
        if (drawing) {
            return InteractionResultHolder.fail(stack);
        }

        boolean onCooldown = getCoolDownId(stack)
                .map(id -> player.getCapability(CustomItemCoolDownsProvider.CAPABILITY)
                        .map(cap -> cap.isOnCooldown(id))
                        .orElse(false)
                ).orElse(false);
        if (onCooldown) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(stack);
    }

    @ParametersAreNonnullByDefault
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        return finishConsumableUse(stack, level, entity);
    }

    @ParametersAreNonnullByDefault
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        this.getConsumableIndex(stack).ifPresent(index -> {
            if (index.getData().isToggleUse() && entity.getTicksUsingItem() >= index.getData().getUseDuration()) {
                if (!level.isClientSide()) {
                    finishConsumableUse(stack, level, entity);
                    entity.stopUsingItem();
                }
            }
        });
    }

    private ItemStack finishConsumableUse(ItemStack stack, Level level, LivingEntity entity) {
        this.getConsumableIndex(stack).ifPresent(index -> {
            if (!level.isClientSide()) {
                applyEffects(entity, stack, index);
                if (entity instanceof Player player) {
                    player.awardStat(Stats.ITEM_USED.get(this));
                    if (!player.getAbilities().instabuild) {
                        consumeAfterUse(stack, entity, index);
                    }
                } else {
                    consumeAfterUse(stack, entity, index);
                }
            }
        });
        return stack;
    }

    private void consumeAfterUse(ItemStack stack, LivingEntity entity, ConsumableIndex index) {
        ConsumableData data = index.getData();
        if (data.hasDurability()) {
            stack.hurtAndBreak(data.getDurabilityDamage(), entity, (living) -> {
                living.broadcastBreakEvent(EquipmentSlot.MAINHAND);
            });
        } else {
            stack.shrink(1);
        }
    }

    private void removeEffect(LivingEntity entity, ConsumableData.RemoveEffectSelector selector) {
        if (selector.isCategory()) {
            removeEffectsByCategory(entity, selector.getCategory());
            return;
        }

        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(selector.getEffect());
        if (effect != null) {
            entity.removeEffect(effect);
        }
    }

    private void removeEffectsByCategory(LivingEntity entity, MobEffectCategory category) {
        List<MobEffect> effects = entity.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(effect -> effect.getCategory() == category)
                .toList();

        for (MobEffect effect : effects) {
            entity.removeEffect(effect);
        }
    }

    private void applyEffects(LivingEntity entity, ItemStack stack, ConsumableIndex index) {
        ConsumableData data = index.getData();
        if (data.getHeal() > 0f) {
            entity.heal(data.getHeal());
        }

        for (ConsumableData.RemoveEffectSelector selector : data.getRemoveEffects()) {
            removeEffect(entity, selector);
        }

        for (ConsumableData.EffectData effectData : data.getEffects()) {
            if (entity.getRandom().nextFloat() > effectData.getChance()) {
                continue;
            }
            MobEffectInstance effect = effectData.createInstance();
            if (effect != null) {
                entity.addEffect(effect);
            }
        }

        if (entity instanceof Player player && (data.getFood() > 0 || data.getSaturation() > 0)) {
            FoodData foodData = player.getFoodData();
            foodData.eat(data.getFood(), data.getSaturation());
        }

        MinecraftForge.EVENT_BUS.post(new ConsumableUseEvent(entity, stack.copy(), index.getId(), index));

        ResourceLocation cooldownId = data.getCooldownCategory();
        if (cooldownId != null && data.getCooldown() > 0) {
            entity.getCapability(CustomItemCoolDownsProvider.CAPABILITY).ifPresent(cap -> cap.addCooldown(cooldownId, data.getCooldown()));
        }
    }

    @Override
    public boolean isSame(ItemStack stack1, ItemStack stack2) {
        return IConsumable.super.isSame(stack1, stack2);
    }

    @ParametersAreNonnullByDefault
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        Component categoryLine = null;
        for (Component c : tooltip) {
            if (c.toString().contains("item_group.")) {
                categoryLine = c;
                break;
            }
        }
        if (categoryLine != null) {
            tooltip.remove(categoryLine);
        }

        boolean showBaseInfo = TooltipHideFlags.shouldShow(stack, GunTooltipPart.BASE_INFO);
        boolean showExtraInfo = TooltipHideFlags.shouldShow(stack, GunTooltipPart.EXTRA_DAMAGE_INFO);
        this.getConsumableIndex(stack).ifPresent(index -> {
            ConsumableData data = index.getData();
            if (showBaseInfo) {
                if (data.getHeal() > 0f) {
                    addStat(tooltip, "tooltip.lrtactical.consumable.heal", formatNumber(data.getHeal()));
                }
                if (data.getFood() > 0) {
                    addStat(tooltip, "tooltip.lrtactical.consumable.food", Integer.toString(data.getFood()));
                }
            }

            if (showExtraInfo) {
                if (data.getSaturation() > 0f) {
                    addStat(tooltip, "tooltip.lrtactical.consumable.saturation",
                            formatNumber(data.getSaturation()));
                }
                addStat(tooltip, "tooltip.lrtactical.consumable.use_duration",
                        Component.translatable("tooltip.lrtactical.consumable.seconds",
                                formatNumber(data.getUseDuration() / 20.0)));

                for (ConsumableData.EffectData effectData : data.getEffects()) {
                    MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectData.getId());
                    if (effect != null) {
                        tooltip.add(effect.getDisplayName());
                    }
                }
            }
        });
        if (TooltipHideFlags.shouldShow(stack, GunTooltipPart.PACK_INFO)
                && level != null && level.isClientSide()) {
            var packInfo = ClientAssetsManager.INSTANCE.getPackInfo(getId(stack));
            if (packInfo != null) {
                tooltip.add(Component.translatable(packInfo.getName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (categoryLine != null) {
            tooltip.add(categoryLine);
        }
    }

    private static void addStat(List<Component> tooltip, String translationKey, String value) {
        addStat(tooltip, translationKey, Component.literal(value));
    }

    private static void addStat(List<Component> tooltip, String translationKey, Component value) {
        tooltip.add(Component.translatable(translationKey)
                .withStyle(TACZ_STAT_LABEL_STYLE)
                .append(value.copy().withStyle(ChatFormatting.AQUA)));
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format("%.0f", value);
        }
        return String.format("%.1f", value);
    }
}
