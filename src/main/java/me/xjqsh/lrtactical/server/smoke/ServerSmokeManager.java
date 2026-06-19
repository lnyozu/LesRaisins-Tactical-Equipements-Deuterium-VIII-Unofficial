package me.xjqsh.lrtactical.server.smoke;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import me.xjqsh.lrtactical.network.NetworkHandler;
import me.xjqsh.lrtactical.network.message.SSmokeState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EquipmentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerSmokeManager {
    public static final double SYNC_RANGE = 128.0D;
    private static final int SYNC_INTERVAL = 20;
    private static final Map<ResourceKey<Level>, Map<UUID, ActiveSmoke>> ACTIVE_SMOKES = new HashMap<>();

    private ServerSmokeManager() {
    }

    public static void register(ServerLevel level, UUID id, Vec3 position, int remainingTicks) {
        if (remainingTicks <= 0) {
            return;
        }
        ActiveSmoke smoke = new ActiveSmoke(position, level.getGameTime() + remainingTicks);
        ACTIVE_SMOKES.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(id, smoke);
        sendState(level, id, smoke, remainingTicks, false);
    }

    public static void remove(ServerLevel level, UUID id) {
        Map<UUID, ActiveSmoke> smokes = ACTIVE_SMOKES.get(level.dimension());
        if (smokes == null) {
            return;
        }
        ActiveSmoke removed = smokes.remove(id);
        if (removed != null) {
            sendState(level, id, removed, 0, true);
        }
        if (smokes.isEmpty()) {
            ACTIVE_SMOKES.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        Map<UUID, ActiveSmoke> smokes = ACTIVE_SMOKES.get(level.dimension());
        if (smokes == null || smokes.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        boolean shouldSync = gameTime % SYNC_INTERVAL == 0L;
        Iterator<Map.Entry<UUID, ActiveSmoke>> iterator = smokes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveSmoke> entry = iterator.next();
            ActiveSmoke smoke = entry.getValue();
            long remaining = smoke.expiresAt - gameTime;
            if (remaining <= 0L) {
                sendState(level, entry.getKey(), smoke, 0, true);
                iterator.remove();
            } else if (shouldSync) {
                sendState(level, entry.getKey(), smoke, (int) Math.min(remaining, Integer.MAX_VALUE), false);
            }
        }
        if (smokes.isEmpty()) {
            ACTIVE_SMOKES.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof SmokeGrenadeEntity
                && event.getLevel() instanceof ServerLevel level) {
            remove(level, event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ACTIVE_SMOKES.remove(level.dimension());
        }
    }

    private static void sendState(ServerLevel level, UUID id, ActiveSmoke smoke,
                                  int remainingTicks, boolean removed) {
        double rangeSqr = SYNC_RANGE * SYNC_RANGE;
        SSmokeState message = new SSmokeState(id, smoke.position, remainingTicks, removed);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(smoke.position) <= rangeSqr) {
                NetworkHandler.sendToClientPlayer(message, player);
            }
        }
    }

    private record ActiveSmoke(Vec3 position, long expiresAt) {
    }
}
