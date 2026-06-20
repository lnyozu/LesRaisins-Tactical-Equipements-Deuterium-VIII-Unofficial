package me.xjqsh.lrtactical.client;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.client.smoke.ClientSmokeManager;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        modid = EquipmentMod.MOD_ID
)
public final class ClientConfigReloadHandler {
    private ClientConfigReloadHandler() {
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (!EquipmentMod.MOD_ID.equals(event.getConfig().getModId())) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            ClientSmokeManager.invalidateAllVolumes();
            SpEffectCloudEntity.refreshClientVisualState();
        });
    }
}
