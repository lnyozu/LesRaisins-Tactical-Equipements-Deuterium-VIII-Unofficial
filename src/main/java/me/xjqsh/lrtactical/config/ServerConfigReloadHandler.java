package me.xjqsh.lrtactical.config;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import me.xjqsh.lrtactical.server.smoke.ServerSmokeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = EquipmentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ServerConfigReloadHandler {
    private ServerConfigReloadHandler() {
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (!EquipmentMod.MOD_ID.equals(event.getConfig().getModId())
                || event.getConfig().getType() != ModConfig.Type.COMMON
                || !ServerConfig.FILE_NAME.equals(event.getConfig().getFileName())) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof SpEffectCloudEntity fireCloud) {
                        fireCloud.invalidateAreaCache();
                    }
                }
            }
            ServerSmokeManager.broadcastConfig();
        });
    }
}
