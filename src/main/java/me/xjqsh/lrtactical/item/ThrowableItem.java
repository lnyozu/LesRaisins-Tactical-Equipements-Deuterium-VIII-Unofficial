package me.xjqsh.lrtactical.item;

import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.item.GunTooltipPart;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.capability.CustomItemCoolDownsProvider;
import me.xjqsh.lrtactical.client.renderer.item.ThrowableItemRendererWrapper;
import me.xjqsh.lrtactical.config.ServerConfig;
import me.xjqsh.lrtactical.init.ModItems;
import me.xjqsh.lrtactical.item.index.ThrowableIndex;
import me.xjqsh.lrtactical.item.throwable.area.EffectCloudThrowableData;
import me.xjqsh.lrtactical.item.throwable.explode.ExplodeThrowableData;
import me.xjqsh.lrtactical.item.throwable.flash.StunThrowableData;
import me.xjqsh.lrtactical.item.throwable.smoke.SmokeType;
import me.xjqsh.lrtactical.server.c4.C4ServerManager;
import me.xjqsh.lrtactical.util.TooltipHideFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.Consumer;

public class ThrowableItem extends Item implements IAnimationItem, IThrowable {
    private static final Style TACZ_STAT_LABEL_STYLE = Style.EMPTY.withColor(0x777777);

    public ThrowableItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.getThrowableIndex(stack).map(ThrowableIndex::getMaxStackSize).orElse(1);
    }

    @ParametersAreNonnullByDefault
    @Override
    public int getUseDuration(ItemStack pStack) {
        return 72000;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack pStack) {
        return UseAnim.BOW;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private ThrowableItemRendererWrapper renderer = null;

            @Override
            public ThrowableItemRendererWrapper getCustomRenderer() {
                if (this.renderer == null) {
                    renderer = new ThrowableItemRendererWrapper();
                }
                return renderer;
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    public void triggerAnimation(ItemStack stack, String animationName) {
        if (IClientItemExtensions.of(stack.getItem()).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            renderer.triggerAnimation(stack, animationName);
        }
    }

    @ParametersAreNonnullByDefault
    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player player, InteractionHand pUsedHand) {
        if (pUsedHand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.fail(player.getItemInHand(pUsedHand));
        }
        ItemStack stack = player.getItemInHand(pUsedHand);
        boolean flag = getThrowableIndex(stack)
                .map(index -> index.getData().getCooldownCategory())
                .map(id -> player.getCapability(CustomItemCoolDownsProvider.CAPABILITY)
                        .map(cap -> cap.isOnCooldown(id))
                        .orElse(false)
                ).orElse(false);
        if (!flag) {
            player.startUsingItem(pUsedHand);
        }
        return InteractionResultHolder.consume(stack);
    }

    public boolean onThrow(Level world, LivingEntity entity, ItemStack stack, ThrowableIndex<?, ?> index) {
        boolean remoteCharge = index.getData() instanceof ExplodeThrowableData explode
                && explode.getExplode().isRemoteDetonation();
        if (remoteCharge
                && entity instanceof ServerPlayer player
                && world instanceof ServerLevel serverLevel
                && !C4ServerManager.canDeploy(serverLevel, player.getUUID())) {
            int count = C4ServerManager.getDeployedCount(serverLevel, player.getUUID());
            player.sendSystemMessage(Component.translatable(
                    "message.lrtactical.c4.limit",
                    count,
                    ServerConfig.getRemoteChargeMaxPerPlayer()
            ).withStyle(ChatFormatting.RED), true);
            return false;
        }

        var throwable = index.createEntity(stack, entity);
        if (index.getData().isCookable()) {
            int newLife = throwable.getLife() - (entity.getTicksUsingItem() - index.getData().getPrepareTime());
            newLife = Math.max(newLife, 0);
            throwable.setLife(newLife);
        }
        if (!world.addFreshEntity(throwable)) {
            return false;
        }
        if (throwable instanceof me.xjqsh.lrtactical.entity.GrenadeEntity grenade
                && grenade.isRemoteDetonation()) {
            C4ServerManager.register(grenade);
        }

        finishThrowableUse(entity, stack, index);

        if (remoteCharge) {
            ItemStack detonatorStack = new ItemStack(ModItems.DETONATOR.get());
            if (detonatorStack.getItem() instanceof DetonatorItem detonatorItem) {
                detonatorItem.recordEntity(throwable, detonatorStack);
            }
            entity.setItemInHand(InteractionHand.MAIN_HAND, detonatorStack);
        }

        return true;
    }

    private void detonateCookedThrowableInHand(LivingEntity entity, ItemStack stack, ThrowableIndex<?, ?> index) {
        var throwable = index.createEntity(stack, entity);
        throwable.setDeltaMovement(0.0D, 0.0D, 0.0D);
        throwable.setPos(entity.getX(), entity.getEyeY() - 0.1D, entity.getZ());
        finishThrowableUse(entity, stack, index);
        throwable.onDeath(null);
    }

    private void finishThrowableUse(LivingEntity entity, ItemStack stack, ThrowableIndex<?, ?> index) {
        ResourceLocation id = index.getData().getCooldownCategory();
        if (id != null && !isCreative(entity)) {
            entity.getCapability(CustomItemCoolDownsProvider.CAPABILITY).ifPresent(cap -> {
                cap.addCooldown(id, index.getData().getCooldown());
            });
        }
        if (!isCreative(entity)) {
            stack.shrink(1);
        }
    }

    private static boolean isCreative(LivingEntity entity) {
        return entity instanceof Player player && player.isCreative();
    }

    private static boolean hasCookedPastFuse(LivingEntity entity, ThrowableIndex<?, ?> index) {
        var data = index.getData();
        int lifeTime = data.getEntityData().getLifeTime();
        return data.isCookable()
                && lifeTime > 0
                && entity.getTicksUsingItem() >= data.getPrepareTime() + lifeTime;
    }

    @ParametersAreNonnullByDefault
    @Override
    public void onUseTick(Level world, LivingEntity entity, ItemStack stack, int pRemainingUseDuration) {
        this.getThrowableIndex(stack).ifPresent(index ->{
            if (hasCookedPastFuse(entity, index)) {
                if (!world.isClientSide()) {
                    entity.stopUsingItem();
                    detonateCookedThrowableInHand(entity, stack, index);
                }
            }
        });
    }

    @ParametersAreNonnullByDefault
    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity entity, int timeLeft) {
        this.getThrowableIndex(stack).ifPresent(index ->{
            if (entity.getTicksUsingItem() >= index.getData().getPrepareTime()) {
                if (!world.isClientSide()) {
                    if (hasCookedPastFuse(entity, index)) {
                        detonateCookedThrowableInHand(entity, stack, index);
                    } else {
                        onThrow(world, entity, stack, index);
                    }
                }
            }
        });
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        super.onStopUsing(stack, entity, count);
    }

    @Override
    public boolean useOnRelease(@NotNull ItemStack pStack) {
        return true;
    }

    @NotNull
    @Override
    public String getDescriptionId(@NotNull ItemStack stack) {
        return this.getThrowableIndex(stack).map(ThrowableIndex::getDescriptionId).orElse(super.getDescriptionId(stack));
    }

    @Override
    public boolean isSame(ItemStack stack1, ItemStack stack2) {
        return IThrowable.super.isSame(stack1, stack2);
    }

    @ParametersAreNonnullByDefault
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        Component categoryLine = null;
        for (Component c : pTooltipComponents) {
            if (c.toString().contains("item_group.")) {
                categoryLine = c;
                break;
            }
        }
        if (categoryLine != null) {
            pTooltipComponents.remove(categoryLine);
        }

        boolean showBaseInfo = TooltipHideFlags.shouldShow(stack, GunTooltipPart.BASE_INFO);
        boolean showExtraInfo = TooltipHideFlags.shouldShow(stack, GunTooltipPart.EXTRA_DAMAGE_INFO);
        this.getThrowableIndex(stack).ifPresent(index -> {
            var data = index.getData();
            if (data instanceof ExplodeThrowableData explodeData) {
                if (showBaseInfo) {
                    addStat(pTooltipComponents, "tooltip.lrtactical.throwable.damage",
                            formatNumber(explodeData.getExplode().getDamage()));
                }
                if (showExtraInfo) {
                    addStat(pTooltipComponents, "tooltip.lrtactical.throwable.range",
                            formatNumber(explodeData.getExplode().getRadius()));
                }
            } else if (data instanceof StunThrowableData stunData) {
                int duration = Math.max(
                        stunData.getStunData().getBlind().getMaxDuration(),
                        stunData.getStunData().getDeafened().getMaxDuration()
                );
                if (showBaseInfo) {
                    addDuration(pTooltipComponents, "tooltip.lrtactical.throwable.max_duration", duration);
                }
                if (showExtraInfo) {
                    addStat(pTooltipComponents, "tooltip.lrtactical.throwable.range",
                            formatNumber(stunData.getStunData().getRadius()));
                }
            } else if (data instanceof EffectCloudThrowableData cloudData) {
                if (showBaseInfo) {
                    addDuration(pTooltipComponents, cloudData.getCloudData().getDuration());
                }
                if (showExtraInfo) {
                    addStat(pTooltipComponents, "tooltip.lrtactical.throwable.range",
                            formatNumber(cloudData.getCloudData().getRadius()));
                    PotionUtils.addPotionTooltip(cloudData.getCloudData().getEffectInstances(), pTooltipComponents, 1.0F);
                }
            } else if (index.getType() == SmokeType.SMOKE) {
                int duration = Math.max(0,
                        ServerConfig.adjustSmokeLifetimeTicks(
                                data.getEntityData().getLifeTime()
                        ) - ServerConfig.getSmokeStartDelayTicks());
                if (showBaseInfo) {
                    addDuration(pTooltipComponents, duration);
                }
                if (showExtraInfo) {
                    addStat(pTooltipComponents, "tooltip.lrtactical.throwable.range",
                            formatNumber(ServerConfig.getSmokeRadius()));
                }
            }
        });
        if (TooltipHideFlags.shouldShow(stack, GunTooltipPart.PACK_INFO)
                && pLevel != null && pLevel.isClientSide()) {
            var packInfo = ClientAssetsManager.INSTANCE.getPackInfo(getId(stack));
            if (packInfo != null) {
                pTooltipComponents.add(Component.translatable(packInfo.getName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (categoryLine != null) {
            pTooltipComponents.add(categoryLine);
        }
    }

    private static void addDuration(List<Component> tooltip, int ticks) {
        addDuration(tooltip, "tooltip.lrtactical.throwable.duration", ticks);
    }

    private static void addDuration(List<Component> tooltip, String translationKey, int ticks) {
        Component value = Component.translatable(
                "tooltip.lrtactical.throwable.seconds",
                formatNumber(ticks / 20.0)
        ).withStyle(ChatFormatting.AQUA);
        tooltip.add(Component.translatable(translationKey)
                .withStyle(TACZ_STAT_LABEL_STYLE)
                .append(value));
    }

    private static void addStat(List<Component> tooltip, String translationKey, String value) {
        tooltip.add(Component.translatable(translationKey)
                .withStyle(TACZ_STAT_LABEL_STYLE)
                .append(Component.literal(value).withStyle(ChatFormatting.AQUA)));
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format("%.0f", value);
        }
        return String.format("%.1f", value);
    }
}
