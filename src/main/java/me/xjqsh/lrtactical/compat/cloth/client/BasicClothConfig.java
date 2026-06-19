package me.xjqsh.lrtactical.compat.cloth.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.xjqsh.lrtactical.config.ClientConfig;
import me.xjqsh.lrtactical.config.CommonConfig;
import net.minecraft.network.chat.Component;

public class BasicClothConfig {
    public static void init(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        root.getOrCreateCategory(Component.translatable("config.lrtactical.effect"))
            .addEntry(
                entryBuilder.startBooleanToggle(Component.translatable("config.lrtactical.effect.blackflash"), ClientConfig.BLACK_FLASH.get())
                    .setDefaultValue(false)
                    .setTooltip(Component.translatable("config.lrtactical.effect.blackflash.desc"))
                    .setSaveConsumer(ClientConfig.BLACK_FLASH::set)
                    .build()
            );

        root.getOrCreateCategory(Component.translatable("config.lrtactical.grenade"))
                .addEntry(
                        entryBuilder.startBooleanToggle(Component.translatable("config.lrtactical.grenade.explode"), CommonConfig.GRENADE_EXPLOSION_BLOCK_DAMAGE.get())
                                .setDefaultValue(false)
                                .setTooltip(Component.translatable("config.lrtactical.grenade.explode.desc"))
                                .setSaveConsumer(CommonConfig.GRENADE_EXPLOSION_BLOCK_DAMAGE::set)
                                .build()
                )
                .addEntry(
                        entryBuilder.startDoubleField(Component.translatable("config.lrtactical.grenade.screen_shake"), ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER.get())
                                .setDefaultValue(1.0)
                                .setTooltip(Component.translatable("config.lrtactical.grenade.screen_shake.desc"))
                                .setSaveConsumer(ClientConfig.EXPLODE_SCREEN_SHAKE_MULTIPLIER::set)
                                .build()
                );

        root.getOrCreateCategory(Component.translatable("config.lrtactical.melee"))
                .addEntry(
                        entryBuilder.startBooleanToggle(Component.translatable("config.lrtactical.melee.durability"), CommonConfig.MELEE_ITEM_CONSUME_DURABILITY.get())
                                .setDefaultValue(false)
                                .setTooltip(Component.translatable("config.lrtactical.melee.durability.desc"))
                                .setSaveConsumer(CommonConfig.MELEE_ITEM_CONSUME_DURABILITY::set)
                                .build()
                )
                .addEntry(
                        entryBuilder.startBooleanToggle(Component.translatable("config.lrtactical.melee.enchantment_effects"), CommonConfig.MELEE_ENCHANTMENT_EFFECTS_ENABLED.get())
                                .setDefaultValue(false)
                                .setTooltip(Component.translatable("config.lrtactical.melee.enchantment_effects.desc"))
                                .setSaveConsumer(CommonConfig.MELEE_ENCHANTMENT_EFFECTS_ENABLED::set)
                                .build()
                )
                .addEntry(
                        entryBuilder.startBooleanToggle(Component.translatable("config.lrtactical.melee.anvil_enchanting"), CommonConfig.MELEE_ANVIL_ENCHANTING_ENABLED.get())
                                .setDefaultValue(false)
                                .setTooltip(Component.translatable("config.lrtactical.melee.anvil_enchanting.desc"))
                                .setSaveConsumer(CommonConfig.MELEE_ANVIL_ENCHANTING_ENABLED::set)
                                .build()
                )
                .addEntry(
                        entryBuilder.startBooleanToggle(Component.translatable("config.lrtactical.melee.table_enchanting"), CommonConfig.MELEE_ENCHANTING_TABLE_ENABLED.get())
                                .setDefaultValue(false)
                                .setTooltip(Component.translatable("config.lrtactical.melee.table_enchanting.desc"))
                                .setSaveConsumer(CommonConfig.MELEE_ENCHANTING_TABLE_ENABLED::set)
                                .build()
                )
                .addEntry(
                        entryBuilder.startIntField(Component.translatable("config.lrtactical.melee.max_target"), CommonConfig.MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD.get())
                                .setDefaultValue(32)
                                .setMin(1)
                                .setMax(512)
                                .setTooltip(Component.translatable("config.lrtactical.effect.max_target.desc"))
                                .setSaveConsumer(CommonConfig.MELEE_IGNORE_INVULNERABLE_TICK_THRESHOLD::set)
                                .build()
                );
    }
}
