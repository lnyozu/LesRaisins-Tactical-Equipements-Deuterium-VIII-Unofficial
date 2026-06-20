package me.xjqsh.lrtactical.command;

import com.mojang.brigadier.CommandDispatcher;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.config.ConfigReloadUtil;
import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import me.xjqsh.lrtactical.entity.ThrowableItemEntity;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import me.xjqsh.lrtactical.network.NetworkHandler;
import me.xjqsh.lrtactical.network.message.SClientEffectControl;
import me.xjqsh.lrtactical.server.smoke.ServerSmokeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = EquipmentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TacticalEffectCommands {
    private TacticalEffectCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("lrtactical")
                .requires(source -> source.hasPermission(4))
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("clear")
                        .then(Commands.literal("all")
                                .executes(context -> clear(context.getSource(), ClearScope.ALL)))
                        .then(Commands.literal("throwables")
                                .executes(context -> clear(context.getSource(), ClearScope.THROWABLES)))
                        .then(Commands.literal("smoke")
                                .executes(context -> clear(context.getSource(), ClearScope.SMOKE)))
                        .then(Commands.literal("fire_clouds")
                                .executes(context -> clear(context.getSource(), ClearScope.FIRE_CLOUDS))))
                .then(Commands.literal("reload")
                        .then(Commands.literal("config")
                                .executes(context -> reloadConfig(context.getSource())))));
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.lrtactical.help.header")
                .withStyle(ChatFormatting.GOLD), false);
        sendHelpLine(source, "/lrtactical help", "command.lrtactical.help.help");
        sendHelpLine(source, "/lrtactical clear all", "command.lrtactical.help.clear_all");
        sendHelpLine(source, "/lrtactical clear throwables", "command.lrtactical.help.clear_throwables");
        sendHelpLine(source, "/lrtactical clear smoke", "command.lrtactical.help.clear_smoke");
        sendHelpLine(source, "/lrtactical clear fire_clouds", "command.lrtactical.help.clear_fire_clouds");
        sendHelpLine(source, "/lrtactical reload config", "command.lrtactical.help.reload_config");
        source.sendSuccess(() -> Component.translatable("command.lrtactical.help.permission")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        source.sendSuccess(() -> Component.translatable("command.lrtactical.help.config_path")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static void sendHelpLine(
            CommandSourceStack source,
            String command,
            String descriptionKey
    ) {
        Component commandComponent = Component.literal(command).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("command.lrtactical.help.click")
                )));
        source.sendSuccess(() -> Component.literal("  ")
                .append(commandComponent)
                .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY)), false);
    }

    private static int clear(CommandSourceStack source, ClearScope scope) {
        int throwableCount = 0;
        int smokeEntityCount = 0;
        int smokeStateCount = 0;
        int fireCloudCount = 0;
        int clientFlags = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (scope.clearsSmoke()) {
                smokeStateCount += ServerSmokeManager.clear(level);
                clientFlags |= SClientEffectControl.CLEAR_SMOKE;
            }

            List<Entity> entities = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (scope.clearsAllThrowables() && entity instanceof ThrowableItemEntity) {
                    entities.add(entity);
                } else if (scope == ClearScope.SMOKE && entity instanceof SmokeGrenadeEntity) {
                    entities.add(entity);
                } else if (scope.clearsFireClouds() && entity instanceof SpEffectCloudEntity) {
                    entities.add(entity);
                }
            }

            for (Entity entity : entities) {
                if (entity instanceof SmokeGrenadeEntity) {
                    smokeEntityCount++;
                } else if (entity instanceof ThrowableItemEntity) {
                    throwableCount++;
                } else if (entity instanceof SpEffectCloudEntity) {
                    fireCloudCount++;
                }
                entity.discard();
            }
        }

        if (scope.clearsFireClouds()) {
            clientFlags |= SClientEffectControl.CLEAR_FIRE_CLOUDS;
        }
        if (clientFlags != 0) {
            NetworkHandler.sendToAllPlayers(new SClientEffectControl(clientFlags));
        }

        int total = throwableCount + smokeEntityCount + smokeStateCount + fireCloudCount;
        int finalThrowableCount = throwableCount;
        int finalSmokeEntityCount = smokeEntityCount;
        int finalSmokeStateCount = smokeStateCount;
        int finalFireCloudCount = fireCloudCount;
        source.sendSuccess(() -> Component.literal(
                "Cleared LR Tactical effects: throwables=" + finalThrowableCount
                        + ", smoke_entities=" + finalSmokeEntityCount
                        + ", smoke_states=" + finalSmokeStateCount
                        + ", fire_clouds=" + finalFireCloudCount
        ), true);
        return total;
    }

    private static int reloadConfig(CommandSourceStack source) {
        try {
            int reloaded = ConfigReloadUtil.reload(ModConfig.Type.COMMON);
            for (ServerLevel level : source.getServer().getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof SpEffectCloudEntity fireCloud) {
                        fireCloud.invalidateAreaCache();
                    }
                }
            }
            ServerSmokeManager.broadcastConfig();
            source.sendSuccess(() -> Component.literal(
                    "Reloaded " + reloaded
                            + " LR Tactical config file(s). Existing smoke/fire caches were invalidated."
            ), true);
            return reloaded;
        } catch (RuntimeException exception) {
            EquipmentMod.LOGGER.error("Failed to reload LR Tactical configuration", exception);
            source.sendFailure(Component.literal(
                    "Failed to reload LR Tactical configuration: " + exception.getMessage()
            ));
            return 0;
        }
    }

    private enum ClearScope {
        ALL,
        THROWABLES,
        SMOKE,
        FIRE_CLOUDS;

        private boolean clearsAllThrowables() {
            return this == ALL || this == THROWABLES;
        }

        private boolean clearsSmoke() {
            return this == ALL || this == THROWABLES || this == SMOKE;
        }

        private boolean clearsFireClouds() {
            return this == ALL || this == FIRE_CLOUDS;
        }
    }
}
