package me.xjqsh.lrtactical.server.smoke;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.config.ServerConfig;
import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import me.xjqsh.lrtactical.network.NetworkHandler;
import me.xjqsh.lrtactical.network.message.SSmokeConfig;
import me.xjqsh.lrtactical.network.message.SSmokeState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EquipmentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerSmokeManager {
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

    public static void updatePosition(ServerLevel level, UUID id, Vec3 position, int remainingTicks) {
        if (remainingTicks <= 0) {
            remove(level, id);
            return;
        }

        Map<UUID, ActiveSmoke> smokes = ACTIVE_SMOKES.get(level.dimension());
        if (smokes == null) {
            register(level, id, position, remainingTicks);
            return;
        }

        ActiveSmoke smoke = smokes.get(id);
        if (smoke == null) {
            register(level, id, position, remainingTicks);
            return;
        }

        boolean moved = smoke.position.distanceToSqr(position) > 0.01D;
        smoke.position = position;
        smoke.expiresAt = level.getGameTime() + remainingTicks;
        if (moved) {
            sendState(level, id, smoke, remainingTicks, false);
        }
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

    public static int clear(ServerLevel level) {
        Map<UUID, ActiveSmoke> removed = ACTIVE_SMOKES.remove(level.dimension());
        return removed == null ? 0 : removed.size();
    }

    public static int disperseByExplosion(ServerLevel level, Vec3 center, double explosionRadius) {
        if (!ServerConfig.isSmokeExplosionDispelEnabled() || explosionRadius <= 0.0D) {
            return 0;
        }

        double smokeRadius = ServerConfig.getSmokeRadius();
        double verticalExtent = ServerConfig.getSmokeShape()
                == me.xjqsh.lrtactical.config.SmokeShape.SPHERE
                ? smokeRadius
                : ServerConfig.getSmokeHeight();
        AABB searchBox = new AABB(center, center).inflate(
                explosionRadius + smokeRadius,
                explosionRadius + verticalExtent,
                explosionRadius + smokeRadius
        );
        ArrayList<SmokeGrenadeEntity> affected = new ArrayList<>();
        for (SmokeGrenadeEntity smoke : level.getEntitiesOfClass(
                SmokeGrenadeEntity.class,
                searchBox
        )) {
            if (smoke.tickCount < ServerConfig.getSmokeStartDelayTicks()) {
                continue;
            }
            if (ServerConfig.getSmokeShape().intersectsExplosion(
                    smoke.position(),
                    center,
                    smokeRadius,
                    ServerConfig.getSmokeHeight(),
                    explosionRadius
            )) {
                affected.add(smoke);
            }
        }
        for (SmokeGrenadeEntity smoke : affected) {
            smoke.onDeath(null);
        }
        return affected.size();
    }

    public static void sendConfig(ServerPlayer player) {
        NetworkHandler.sendToClientPlayer(new SSmokeConfig(
                ServerConfig.getSmokeShape().networkId(),
                ServerConfig.getSmokeRadius(),
                ServerConfig.getSmokeHeight(),
                ServerConfig.getFireCloudMaxCells(),
                ServerConfig.getFireCloudStepUp(),
                ServerConfig.getFireCloudDropDown(),
                ServerConfig.getFireCloudCacheRefreshTicks()
        ), player);
    }

    public static void broadcastConfig() {
        NetworkHandler.sendToAllPlayers(new SSmokeConfig(
                ServerConfig.getSmokeShape().networkId(),
                ServerConfig.getSmokeRadius(),
                ServerConfig.getSmokeHeight(),
                ServerConfig.getFireCloudMaxCells(),
                ServerConfig.getFireCloudStepUp(),
                ServerConfig.getFireCloudDropDown(),
                ServerConfig.getFireCloudCacheRefreshTicks()
        ));
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
        if (gameTime % ServerConfig.getSmokeSyncIntervalTicks() != 0L) {
            return;
        }

        Iterator<Map.Entry<UUID, ActiveSmoke>> iterator = smokes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveSmoke> entry = iterator.next();
            ActiveSmoke smoke = entry.getValue();
            long remaining = smoke.expiresAt - gameTime;
            if (remaining <= 0L) {
                sendState(level, entry.getKey(), smoke, 0, true);
                iterator.remove();
            } else {
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
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendConfig(player);
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
        double syncRange = ServerConfig.getSmokeSyncRange();
        double rangeSqr = syncRange * syncRange;
        SSmokeState message = new SSmokeState(id, smoke.position, remainingTicks, removed);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(smoke.position) <= rangeSqr) {
                NetworkHandler.sendToClientPlayer(message, player);
            }
        }
    }

    private static final class ActiveSmoke {
        private Vec3 position;
        private long expiresAt;

        private ActiveSmoke(Vec3 position, long expiresAt) {
            this.position = position;
            this.expiresAt = expiresAt;
        }
    }
}
