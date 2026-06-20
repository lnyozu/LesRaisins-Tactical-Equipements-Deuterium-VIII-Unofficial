package me.xjqsh.lrtactical.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraft.world.phys.Vec3;

public class ServerConfig {
    public static final String FILE_NAME = "lrtactical-server.toml";

    public static ForgeConfigSpec.IntValue FLASH_SHIELD_MAX_DURABILITY;
    public static ForgeConfigSpec.IntValue FLASH_SHIELD_COOLDOWN;

    public static ForgeConfigSpec.DoubleValue CROUCHING_INIT_SPEED_PERCENT;
    public static ForgeConfigSpec.IntValue THROWABLE_FORCE_CLEANUP_TIME_SECONDS;
    public static ForgeConfigSpec.IntValue REMOTE_CHARGE_FORCE_CLEANUP_TIME_SECONDS;
    public static ForgeConfigSpec.IntValue REMOTE_CHARGE_MAX_PER_PLAYER;
    public static ForgeConfigSpec.IntValue REMOTE_CHARGE_CHAIN_DETONATIONS_PER_TICK;

    public static ForgeConfigSpec.IntValue SMOKE_START_DELAY_TICKS;
    public static ForgeConfigSpec.ConfigValue<String> SMOKE_SHAPE;
    public static ForgeConfigSpec.DoubleValue SMOKE_RADIUS;
    public static ForgeConfigSpec.DoubleValue SMOKE_HEIGHT;
    public static ForgeConfigSpec.DoubleValue SMOKE_DURATION_MULTIPLIER;
    public static ForgeConfigSpec.IntValue SMOKE_MAX_LIFETIME_TICKS;
    public static ForgeConfigSpec.DoubleValue SMOKE_SYNC_RANGE;
    public static ForgeConfigSpec.IntValue SMOKE_SYNC_INTERVAL_TICKS;
    public static ForgeConfigSpec.IntValue SMOKE_MOVING_SYNC_INTERVAL_TICKS;
    public static ForgeConfigSpec.IntValue SMOKE_MOTION_SLEEP_CONFIRM_TICKS;
    public static ForgeConfigSpec.BooleanValue SMOKE_EXPLOSION_DISPEL_ENABLED;

    public static ForgeConfigSpec.IntValue FIRE_CLOUD_EFFECT_INTERVAL_TICKS;
    public static ForgeConfigSpec.IntValue FIRE_CLOUD_MAX_CELLS;
    public static ForgeConfigSpec.IntValue FIRE_CLOUD_STEP_UP;
    public static ForgeConfigSpec.IntValue FIRE_CLOUD_DROP_DOWN;
    public static ForgeConfigSpec.IntValue FIRE_CLOUD_CACHE_REFRESH_TICKS;
    public static ForgeConfigSpec.BooleanValue FIRE_CLOUD_SMOKE_EXTINGUISH_ENABLED;

    public static ForgeConfigSpec.IntValue MELEE_MAX_TARGET_PER_PACKET;

    public static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("flash shield");
        FLASH_SHIELD_MAX_DURABILITY = builder
                .comment("max durability of flash shield")
                .defineInRange("flash_shield_max_durability", 450, 1, 32767);
        FLASH_SHIELD_COOLDOWN = builder
                .comment("cooldown of flash shield in ticks")
                .defineInRange("flash_shield_cooldown", 200, 20, 32767);
        builder.pop();

        builder.push("throwable");
        CROUCHING_INIT_SPEED_PERCENT = builder
                .comment("modifier of initial speed when crouching, 1.0 means no change")
                .defineInRange("flash_shield_cooldown", 0.5f, 0.01, 2);
        THROWABLE_FORCE_CLEANUP_TIME_SECONDS = builder
                .comment("Maximum lifetime of any throwable entity before it is silently removed; does not trigger its fuse effect")
                .defineInRange("throwable_force_cleanup_time_seconds", 30 * 60, 1, 24 * 60 * 60);
        REMOTE_CHARGE_FORCE_CLEANUP_TIME_SECONDS = builder
                .comment("Maximum lifetime of an armed remote charge such as C4; 0 disables automatic cleanup")
                .defineInRange("remote_charge_force_cleanup_time_seconds", 30 * 60, 0, 7 * 24 * 60 * 60);
        REMOTE_CHARGE_MAX_PER_PLAYER = builder
                .comment("Maximum number of persistent remote charges one player may have deployed")
                .defineInRange("remote_charge_max_per_player", 16, 1, 256);
        REMOTE_CHARGE_CHAIN_DETONATIONS_PER_TICK = builder
                .comment("Maximum number of explosion-triggered remote charges detonated per server tick")
                .defineInRange("remote_charge_chain_detonations_per_tick", 8, 1, 64);
        builder.pop();

        builder.push("smoke");
        SMOKE_START_DELAY_TICKS = builder
                .comment("Delay before a smoke grenade starts producing smoke")
                .defineInRange("start_delay_ticks", 40, 0, 20 * 60);
        SMOKE_SHAPE = builder
                .comment("Smoke volume preset: sphere or cylinder")
                .define("shape", SmokeShape.SPHERE.configName(), SmokeShape::isValidConfigValue);
        SMOKE_RADIUS = builder
                .comment("Smoke radius in blocks; used by both sphere and cylinder presets")
                .defineInRange("radius", 5.5D, 0.5D, 32.0D);
        SMOKE_HEIGHT = builder
                .comment("Cylinder half-height above and below its center; ignored by sphere preset")
                .defineInRange("half_height", 4.5D, 0.5D, 32.0D);
        SMOKE_DURATION_MULTIPLIER = builder
                .comment("Multiplier applied to each smoke grenade Index entity.life_time")
                .defineInRange("duration_multiplier", 1.0D, 0.05D, 16.0D);
        SMOKE_MAX_LIFETIME_TICKS = builder
                .comment("Maximum total smoke grenade lifetime after the Index duration multiplier")
                .defineInRange("max_lifetime_ticks", 20 * 60 * 10, 1, 20 * 60 * 60);
        SMOKE_SYNC_RANGE = builder
                .comment("Range in blocks used to synchronize active smoke state")
                .defineInRange("sync_range", 128.0D, 16.0D, 512.0D);
        SMOKE_SYNC_INTERVAL_TICKS = builder
                .comment("Periodic active smoke state synchronization interval")
                .defineInRange("sync_interval_ticks", 20, 1, 200);
        SMOKE_MOVING_SYNC_INTERVAL_TICKS = builder
                .comment("Position synchronization interval while a smoke grenade is moving")
                .defineInRange("moving_sync_interval_ticks", 4, 1, 40);
        SMOKE_MOTION_SLEEP_CONFIRM_TICKS = builder
                .comment("Low-motion ticks required before a smoke grenade stops server collision simulation")
                .defineInRange("motion_sleep_confirm_ticks", 5, 1, 100);
        SMOKE_EXPLOSION_DISPEL_ENABLED = builder
                .comment("Whether LR Tactical explosions disperse overlapping active smoke clouds")
                .define("explosion_dispel_enabled", true);
        builder.pop();

        builder.push("fire_cloud");
        FIRE_CLOUD_EFFECT_INTERVAL_TICKS = builder
                .comment("Interval between fire cloud damage and status-effect checks")
                .defineInRange("effect_interval_ticks", 10, 1, 100);
        FIRE_CLOUD_MAX_CELLS = builder
                .comment("Maximum terrain cells occupied by one fire cloud")
                .defineInRange("max_cells", 192, 16, 4096);
        FIRE_CLOUD_STEP_UP = builder
                .comment("Maximum number of blocks a fire cloud can spread upward")
                .defineInRange("step_up", 1, 0, 8);
        FIRE_CLOUD_DROP_DOWN = builder
                .comment("Maximum number of blocks searched downward for connected terrain")
                .defineInRange("drop_down", 3, 0, 16);
        FIRE_CLOUD_CACHE_REFRESH_TICKS = builder
                .comment("Maximum lifetime of a cached fire-cloud terrain area")
                .defineInRange("cache_refresh_ticks", 40, 5, 20 * 60);
        FIRE_CLOUD_SMOKE_EXTINGUISH_ENABLED = builder
                .comment("Whether fire clouds whose Index allows it can be extinguished by smoke")
                .define("smoke_extinguish_enabled", true);
        builder.pop();

        builder.push("melee");
        MELEE_MAX_TARGET_PER_PACKET = builder
                .comment("maximum number of targets can contains in melee attack request packet")
                .defineInRange("melee_max_target", 32, 1, 512);
        builder.pop();

        return builder.build();
    }

    public static int getThrowableForceCleanupTimeTicks() {
        return THROWABLE_FORCE_CLEANUP_TIME_SECONDS.get() * 20;
    }

    public static int getRemoteChargeForceCleanupTimeTicks() {
        return REMOTE_CHARGE_FORCE_CLEANUP_TIME_SECONDS.get() * 20;
    }

    public static int getRemoteChargeMaxPerPlayer() {
        return REMOTE_CHARGE_MAX_PER_PLAYER.get();
    }

    public static int getRemoteChargeChainDetonationsPerTick() {
        return REMOTE_CHARGE_CHAIN_DETONATIONS_PER_TICK.get();
    }

    public static int getSmokeStartDelayTicks() {
        return SMOKE_START_DELAY_TICKS.get();
    }

    public static double getSmokeRadius() {
        return SMOKE_RADIUS.get();
    }

    public static SmokeShape getSmokeShape() {
        return SmokeShape.fromConfig(SMOKE_SHAPE.get());
    }

    public static double getSmokeHeight() {
        return SMOKE_HEIGHT.get();
    }

    public static boolean isInsideSmokeShape(Vec3 center, Vec3 point) {
        return getSmokeShape().contains(
                center,
                point,
                getSmokeRadius(),
                getSmokeHeight()
        );
    }

    public static int adjustSmokeLifetimeTicks(int indexLifetimeTicks) {
        long adjusted = Math.round(indexLifetimeTicks * SMOKE_DURATION_MULTIPLIER.get());
        return (int) Math.max(1L, Math.min(adjusted, SMOKE_MAX_LIFETIME_TICKS.get()));
    }

    public static double getSmokeSyncRange() {
        return SMOKE_SYNC_RANGE.get();
    }

    public static int getSmokeSyncIntervalTicks() {
        return SMOKE_SYNC_INTERVAL_TICKS.get();
    }

    public static int getSmokeMovingSyncIntervalTicks() {
        return SMOKE_MOVING_SYNC_INTERVAL_TICKS.get();
    }

    public static int getSmokeMotionSleepConfirmTicks() {
        return SMOKE_MOTION_SLEEP_CONFIRM_TICKS.get();
    }

    public static boolean isSmokeExplosionDispelEnabled() {
        return SMOKE_EXPLOSION_DISPEL_ENABLED.get();
    }

    public static int getFireCloudEffectIntervalTicks() {
        return FIRE_CLOUD_EFFECT_INTERVAL_TICKS.get();
    }

    public static int getFireCloudMaxCells() {
        return FIRE_CLOUD_MAX_CELLS.get();
    }

    public static int getFireCloudStepUp() {
        return FIRE_CLOUD_STEP_UP.get();
    }

    public static int getFireCloudDropDown() {
        return FIRE_CLOUD_DROP_DOWN.get();
    }

    public static int getFireCloudCacheRefreshTicks() {
        return FIRE_CLOUD_CACHE_REFRESH_TICKS.get();
    }

    public static boolean isFireCloudSmokeExtinguishEnabled() {
        return FIRE_CLOUD_SMOKE_EXTINGUISH_ENABLED.get();
    }
}
