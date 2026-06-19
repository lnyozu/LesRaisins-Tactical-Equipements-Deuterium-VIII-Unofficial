package me.xjqsh.lrtactical.client.smoke;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import me.xjqsh.lrtactical.init.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = EquipmentMod.MOD_ID)
public final class ClientSmokeManager {
    private static final double RENDER_RANGE_SQR = 128.0D * 128.0D;
    private static final int MAX_PARTICLES_PER_TICK = 64;
    private static final Map<UUID, ActiveSmoke> ACTIVE_SMOKES = new LinkedHashMap<>();

    private static ClientLevel trackedLevel;
    private static int clientTicks;

    private ClientSmokeManager() {
    }

    public static void update(UUID id, Vec3 position, int remainingTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (trackedLevel != level) {
            clear();
            trackedLevel = level;
        }
        ACTIVE_SMOKES.compute(id, (key, existing) -> {
            if (existing == null) {
                return new ActiveSmoke(position, remainingTicks);
            }
            existing.position = position;
            existing.remainingTicks = remainingTicks;
            return existing;
        });
    }

    public static void remove(UUID id) {
        ACTIVE_SMOKES.remove(id);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            clear();
            return;
        }
        if (trackedLevel != level) {
            clear();
            trackedLevel = level;
        }

        clientTicks++;
        int particleBudget = MAX_PARTICLES_PER_TICK;
        Iterator<ActiveSmoke> iterator = ACTIVE_SMOKES.values().iterator();
        while (iterator.hasNext()) {
            ActiveSmoke smoke = iterator.next();
            if (--smoke.remainingTicks <= 0) {
                iterator.remove();
                continue;
            }

            double distanceSqr = player.position().distanceToSqr(smoke.position);
            if (distanceSqr > RENDER_RANGE_SQR || particleBudget <= 0) {
                continue;
            }

            int requested = particleCount(distanceSqr);
            int count = Math.min(requested, particleBudget);
            for (int i = 0; i < count; i++) {
                spawnParticle(level, smoke.position);
            }
            particleBudget -= count;
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && isInsideSmoke(entity)) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static int particleCount(double distanceSqr) {
        if (distanceSqr <= 32.0D * 32.0D) {
            return 8;
        }
        if (distanceSqr <= 64.0D * 64.0D) {
            return 4;
        }
        if (distanceSqr <= 96.0D * 96.0D) {
            return 2;
        }
        return clientTicks % 2 == 0 ? 1 : 0;
    }

    private static void spawnParticle(ClientLevel level, Vec3 center) {
        double offsetX = level.random.triangle(0.0D, SmokeGrenadeEntity.SMOKE_RADIUS);
        double offsetY = level.random.triangle(0.0D, SmokeGrenadeEntity.SMOKE_HEIGHT);
        double offsetZ = level.random.triangle(0.0D, SmokeGrenadeEntity.SMOKE_RADIUS);
        level.addParticle(ModParticleTypes.SMOKE_CLOUD.get(), true,
                center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                0.0D, 0.0D, 0.0D);
    }

    private static boolean isInsideSmoke(Entity entity) {
        double minY = entity.getY();
        double maxY = minY + entity.getBbHeight();
        for (ActiveSmoke smoke : ACTIVE_SMOKES.values()) {
            double dx = entity.getX() - smoke.position.x;
            double dz = entity.getZ() - smoke.position.z;
            if (dx * dx + dz * dz > SmokeGrenadeEntity.SMOKE_RADIUS * SmokeGrenadeEntity.SMOKE_RADIUS) {
                continue;
            }
            double smokeMinY = smoke.position.y - SmokeGrenadeEntity.SMOKE_HEIGHT;
            double smokeMaxY = smoke.position.y + SmokeGrenadeEntity.SMOKE_HEIGHT;
            if (maxY >= smokeMinY && minY <= smokeMaxY) {
                return true;
            }
        }
        return false;
    }

    private static void clear() {
        ACTIVE_SMOKES.clear();
        trackedLevel = null;
        clientTicks = 0;
    }

    private static final class ActiveSmoke {
        private Vec3 position;
        private int remainingTicks;

        private ActiveSmoke(Vec3 position, int remainingTicks) {
            this.position = position;
            this.remainingTicks = remainingTicks;
        }
    }
}
