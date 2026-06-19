package me.xjqsh.lrtactical.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.item.GunTooltipPart;
import me.xjqsh.lrtactical.api.collision.ITargetFilter;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.client.audio.ICustomSoundSupplier;
import me.xjqsh.lrtactical.client.renderer.item.MeleeItemRenderer;
import me.xjqsh.lrtactical.client.tooltip.TooltipSpacer;
import me.xjqsh.lrtactical.config.CommonConfig;
import me.xjqsh.lrtactical.item.index.MeleeWeaponIndex;
import me.xjqsh.lrtactical.item.melee.AttributeData;
import me.xjqsh.lrtactical.item.melee.CombatData;
import me.xjqsh.lrtactical.util.TooltipHideFlags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MeleeItem extends Item implements IAnimationItem, IMeleeWeapon {
    private static final Style TACZ_STAT_LABEL_STYLE = Style.EMPTY.withColor(0x777777);
    private static final DecimalFormat TACZ_DAMAGE_PERCENT_FORMAT = new DecimalFormat("#.##%");
    private static final DecimalFormat TACZ_MOVEMENT_PERCENT_FORMAT = new DecimalFormat("#.#%");
    private static final double PLAYER_BASE_MOVEMENT_SPEED = 0.1D;
    private static final double DEFAULT_CRITICAL_DAMAGE_MULTIPLIER = 1.5D;

    public MeleeItem() {
        super(new Properties().stacksTo(1).setNoRepair());
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return ImmutableMultimap.of();
        }

        // 攻击伤害由近战系统自行结算，避免再次叠加玩家原生的 1 点伤害；
        // 其余配置属性（例如移动速度）仍按正常装备属性生效。
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        this.getMeleeIndex(stack).ifPresent(index ->
                index.getDefaultModifiers().entries().stream()
                        .filter(entry -> entry.getKey() != Attributes.ATTACK_DAMAGE)
                        .forEach(entry -> builder.put(entry.getKey(), entry.getValue()))
        );
        return builder.build();
    }

    @Override
    public int getDefaultTooltipHideFlags(ItemStack stack) {
        return super.getDefaultTooltipHideFlags(stack) | ItemStack.TooltipPart.MODIFIERS.getMask();
    }


    @Override
    public boolean isSame(ItemStack stack1, ItemStack stack2) {
        return IMeleeWeapon.super.isSame(stack1, stack2);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private MeleeItemRenderer renderer = null;

            @Override
            public MeleeItemRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    renderer = new MeleeItemRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack pStack) {
        return CommonConfig.MELEE_ENCHANTING_TABLE_ENABLED.get();
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        if (!CommonConfig.MELEE_ENCHANTING_TABLE_ENABLED.get()) {
            return 0;
        }
        return this.getMeleeIndex(stack)
                .map(index -> index.getData().getEnchantmentValue())
                .orElse(0);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return CommonConfig.MELEE_ANVIL_ENCHANTING_ENABLED.get();
    }

    @NotNull
    @Override
    public String getDescriptionId(@NotNull ItemStack stack) {
        return this.getMeleeIndex(stack).map(MeleeWeaponIndex::getDescriptionId).orElse(super.getDescriptionId(stack));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack pStack) {
        return super.getTooltipImage(pStack);
    }

    @Override
    public int getAttackCoolDown(ItemStack stack, MeleeAction action, int cnt) {
        return this.getMeleeIndex(stack)
                .map(index -> index.getData().getAttackInfo())
                .map(attackInfos -> attackInfos.getAttackInfo(action, cnt))
                .map(CombatData.MeleeAttackInfo::getCooldown)
                .orElse(0);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return this.getMeleeIndex(stack).map(MeleeWeaponIndex::getMaxDurability).orElse(0);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return this.getMaxDamage(stack) > 0;
    }

    @Override
    public int getDrawTime(ItemStack stack) {
        return getMeleeIndex(stack).map(index -> index.getData().getDrawTime()).orElse(0);
    }

    @Override
    public int getPutAwayTime(ItemStack stack) {
        return getMeleeIndex(stack).map(index -> index.getData().getPutAwayTime()).orElse(0);
    }

    @Override
    public int getAttackDelay(Player attacker, ItemStack stack, MeleeAction action, int cnt) {
        return getMeleeIndex(stack)
                .map(index -> index.getData().getAttackInfo())
                .map(attackInfos -> attackInfos.getAttackInfo(action, cnt))
                .map(CombatData.MeleeAttackInfo::getDelay)
                .orElse(0);
    }

    @Override
    public CombatData.MeleeMovement getAttackMovement(Player entity, ItemStack stack, MeleeAction action) {
        return getMeleeIndex(stack)
                .map(index -> index.getData().getAttackInfo())
                .map(attackInfos -> attackInfos.getAttackInfo(action))
                .map(CombatData.MeleeAttackInfo::getMovement)
                .orElse(null);
    }

    @Override
    public List<Entity> collectTargets(Player attacker, ItemStack stack, MeleeAction action, Vec3 origin, Vec3 direction) {
        List<Entity> entities = new ArrayList<>();
        this.getMeleeIndex(stack).ifPresent(index -> {
            CombatData combatData = index.getData().getAttackInfo();
            if (combatData == null) {
                return;
            }
            var attackInfo = combatData.getAttackInfo(action);
            if (attackInfo == null) {
                return;
            }
            ITargetFilter filter = attackInfo.getHitbox();
            entities.addAll(filter.filterTargets(attacker, origin, direction));
        });
        return entities;
    }

    @Override
    public void attack(Player attacker, ItemStack stack, MeleeAction action, List<Entity> targets, int combo) {
        // generic.attack_damage 直接作为武器基础伤害，不叠加玩家实体自带的 1 点伤害
        float base = this.getMeleeIndex(stack)
                .flatMap(i -> i.getData().getRawAttributes().getAttributes().stream()
                        .filter(a -> a.id().getPath().equals("generic.attack_damage"))
                        .findFirst())
                .map(AttributeData.AttributeInfo::amount)
                .orElse(0.0f);
        this.getMeleeIndex(stack).ifPresent(index -> {
            CombatData combatData = index.getData().getAttackInfo();
            if (combatData == null) {
                return;
            }
            var attackInfo = combatData.getAttackInfo(action, combo);
            if (attackInfo == null) {
                return;
            }
            ITargetFilter filter = attackInfo.getHitbox();
            IMeleeWeapon.playMeleeSound(attacker, index.getId(), action.getId(), 1.0f, 1.0f, true);

            float damage = base * attackInfo.getFactor();
            float knockback = attackInfo.getKnockback();

            if (damage <= 0) return;
            boolean hit = false;
            boolean crit = false;
            boolean kill = false;
            for (Entity livingentity : targets) {
                boolean flag = !(livingentity instanceof ArmorStand armorStand) || !armorStand.isMarker();
                boolean inRange = livingentity.distanceToSqr(attacker) <= filter.getMaxRange() * filter.getMaxRange();

                if (livingentity != attacker && flag && inRange) {
                    var result = this.performAttack(attacker, livingentity, stack, damage, knockback);
                    hit |= result.hit();
                    crit |= result.crit();
                    kill |= result.kill();
                }
            }

            if (hit) {
                if (CommonConfig.MELEE_ITEM_CONSUME_DURABILITY.get()) {
                    stack.hurtAndBreak(attackInfo.getDurabilityDamage(), attacker, (player) -> {
                        player.broadcastBreakEvent(EquipmentSlot.MAINHAND);
                    });
                }
                String soundKey;
                if (kill) {
                    soundKey = "kill";
                } else if (crit) {
                    soundKey = "crit";
                } else {
                    soundKey = action.getId() + "_hit";
                }
                IMeleeWeapon.playMeleeSoundToAttacker(attacker, index.getId(),
                        soundKey + ICustomSoundSupplier.FEEDBACK_SUFFIX, 0.5f, 1.0f);
            }
        });
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return CommonConfig.MELEE_ENCHANTING_TABLE_ENABLED.get()
                && enchantment.category == EnchantmentCategory.WEAPON;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return toolAction == ToolActions.SWORD_SWEEP;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        // 把创造模式标签页名称（如 "LesRaisins - 近战武器"）移到 tooltip 最后一行
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
        int statStart = tooltip.size();
        this.getMeleeIndex(stack).ifPresent(index -> {
            var data = index.getData();
            var combatData = data.getAttackInfo();
            if (combatData == null) return;

            if (showBaseInfo) {
                // 基础参数：伤害、攻速、攻击范围
                data.getRawAttributes().getAttributes().stream()
                        .filter(a -> a.id().getPath().equals("generic.attack_damage"))
                        .findFirst()
                        .ifPresent(attr -> tooltip.add(
                                Component.translatable("tooltip.lrtactical.melee.damage")
                                        .withStyle(TACZ_STAT_LABEL_STYLE)
                                        .append(Component.literal(String.format("%.0f", attr.amount()))
                                                .withStyle(ChatFormatting.AQUA))));

                combatData.attackInfo.values().stream()
                        .flatMap(List::stream)
                        .mapToInt(CombatData.MeleeAttackInfo::getCooldown)
                        .min()
                        .ifPresent(cd -> {
                            double speed = 20.0 / cd;
                            tooltip.add(Component.translatable("tooltip.lrtactical.melee.attack_speed")
                                    .withStyle(TACZ_STAT_LABEL_STYLE)
                                    .append(Component.literal(String.format("%.1f", speed))
                                            .withStyle(ChatFormatting.AQUA)));
                        });

                combatData.attackInfo.values().stream()
                        .flatMap(List::stream)
                        .mapToDouble(info -> info.getHitbox().getMaxRange())
                        .max()
                        .ifPresent(range -> tooltip.add(
                                Component.translatable("tooltip.lrtactical.melee.range")
                                        .withStyle(TACZ_STAT_LABEL_STYLE)
                                        .append(Component.literal(String.format("%.1f", range))
                                                .withStyle(ChatFormatting.AQUA))));
            }

            if (showExtraInfo) {
                if (tooltip.size() > statStart) {
                    tooltip.add(TooltipSpacer.marker());
                }

                // 对齐 TACZ EXTRA_DAMAGE_INFO：百分比在前，整行使用强调色。
                double heavyDamageMultiplier = combatData.attackInfo
                        .getOrDefault(MeleeAction.RIGHT, List.of())
                        .stream()
                        .mapToDouble(CombatData.MeleeAttackInfo::getFactor)
                        .max()
                        .orElse(1.0D);
                tooltip.add(Component.translatable(
                                "tooltip.lrtactical.melee.heavy_damage",
                                TACZ_DAMAGE_PERCENT_FORMAT.format(heavyDamageMultiplier))
                        .withStyle(ChatFormatting.GOLD));

                tooltip.add(Component.translatable(
                                "tooltip.lrtactical.melee.critical_damage",
                                TACZ_DAMAGE_PERCENT_FORMAT.format(DEFAULT_CRITICAL_DAMAGE_MULTIPLIER))
                        .withStyle(ChatFormatting.GOLD));

                double movementSpeed = data.getRawAttributes().getAttributes().stream()
                        .filter(a -> a.id().getPath().equals("generic.movement_speed"))
                        .findFirst()
                        .map(attr -> attr.operation() == AttributeModifier.Operation.ADDITION
                                ? attr.amount() / PLAYER_BASE_MOVEMENT_SPEED
                                : (double) attr.amount())
                        .orElse(0.0D);
                tooltip.add(Component.translatable(
                                "tooltip.lrtactical.melee.movement_speed",
                                TACZ_MOVEMENT_PERCENT_FORMAT.format(movementSpeed))
                        .withStyle(ChatFormatting.RED));
            }
        });

        boolean hasStatLines = tooltip.size() > statStart;

        // 包来源
        if (TooltipHideFlags.shouldShow(stack, GunTooltipPart.PACK_INFO)
                && level != null && level.isClientSide()) {
            var packInfo = ClientAssetsManager.INSTANCE.getPackInfo(getId(stack));
            if (packInfo != null) {
                if (hasStatLines) {
                    tooltip.add(TooltipSpacer.marker());
                }
                tooltip.add(Component.translatable(packInfo.getName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        // 创造模式标签页名称移到最后一行
        if (categoryLine != null) {
            tooltip.add(categoryLine);
        }
    }
}
