package me.xjqsh.lrtactical.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static ForgeConfigSpec.BooleanValue BLACK_FLASH;
    public static ForgeConfigSpec.DoubleValue EXPLODE_SCREEN_SHAKE_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue SMOKE_RENDER_DISTANCE;
    public static ForgeConfigSpec.IntValue SMOKE_MAX_PARTICLES_PER_TICK;
    public static ForgeConfigSpec.IntValue SMOKE_NEAR_PARTICLES_PER_TICK;
    public static ForgeConfigSpec.IntValue SMOKE_MEDIUM_PARTICLES_PER_TICK;
    public static ForgeConfigSpec.IntValue SMOKE_FAR_PARTICLES_PER_TICK;
    public static ForgeConfigSpec.IntValue SMOKE_MIN_EXPANSION_TICKS;
    public static ForgeConfigSpec.IntValue SMOKE_MAX_EXPANSION_TICKS;
    public static ForgeConfigSpec.IntValue SMOKE_VOLUME_REBUILD_INTERVAL;
    public static ForgeConfigSpec.IntValue SMOKE_MAX_VOLUME_CELLS;
    public static ForgeConfigSpec.BooleanValue SMOKE_OVERLAP_DEDUPLICATION;
    public static ForgeConfigSpec.IntValue SMOKE_PARTICLE_MIN_LIFETIME;
    public static ForgeConfigSpec.IntValue SMOKE_PARTICLE_MAX_LIFETIME;
    public static ForgeConfigSpec.IntValue SMOKE_PARTICLE_FADE_IN_TICKS;
    public static ForgeConfigSpec.IntValue SMOKE_PARTICLE_FADE_OUT_TICKS;
    public static ForgeConfigSpec.IntValue FIRE_CLOUD_MAX_PARTICLES_PER_TICK;
    public static ForgeConfigSpec.DoubleValue FIRE_CLOUD_PARTICLE_DENSITY;
    public static ForgeConfigSpec.BooleanValue FIRE_CLOUD_OVERLAP_DEDUPLICATION;

    public static ForgeConfigSpec init() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Use black overlay instead of white when blinded by flashbang");
        BLACK_FLASH = builder.define("blackFlash", false);
        EXPLODE_SCREEN_SHAKE_MULTIPLIER = builder
                .comment("Screen shake multiplier for explosions, default is 1.0")
                .defineInRange("explodeScreenShakeMultiplier", 1.0, 0.0, 128.0);

        builder.push("smoke");
        SMOKE_RENDER_DISTANCE = builder
                .comment("Maximum distance in blocks at which smoke particles are generated")
                .defineInRange("render_distance", 128.0D, 16.0D, 512.0D);
        SMOKE_MAX_PARTICLES_PER_TICK = builder
                .comment("Global smoke particle generation budget per client tick")
                .defineInRange("max_particles_per_tick", 192, 8, 2048);
        SMOKE_NEAR_PARTICLES_PER_TICK = builder
                .defineInRange("near_particles_per_tick", 36, 1, 256);
        SMOKE_MEDIUM_PARTICLES_PER_TICK = builder
                .defineInRange("medium_particles_per_tick", 18, 1, 128);
        SMOKE_FAR_PARTICLES_PER_TICK = builder
                .defineInRange("far_particles_per_tick", 8, 1, 64);
        SMOKE_MIN_EXPANSION_TICKS = builder
                .defineInRange("min_expansion_ticks", 24, 1, 200);
        SMOKE_MAX_EXPANSION_TICKS = builder
                .defineInRange("max_expansion_ticks", 50, 1, 400);
        SMOKE_VOLUME_REBUILD_INTERVAL = builder
                .defineInRange("volume_rebuild_interval_ticks", 3, 1, 100);
        SMOKE_MAX_VOLUME_CELLS = builder
                .defineInRange("max_volume_cells", 1024, 64, 8192);
        SMOKE_OVERLAP_DEDUPLICATION = builder
                .define("overlap_deduplication", true);
        SMOKE_PARTICLE_MIN_LIFETIME = builder
                .defineInRange("particle_min_lifetime_ticks", 38, 4, 200);
        SMOKE_PARTICLE_MAX_LIFETIME = builder
                .defineInRange("particle_max_lifetime_ticks", 53, 4, 400);
        SMOKE_PARTICLE_FADE_IN_TICKS = builder
                .defineInRange("particle_fade_in_ticks", 8, 0, 100);
        SMOKE_PARTICLE_FADE_OUT_TICKS = builder
                .defineInRange("particle_fade_out_ticks", 10, 0, 100);
        builder.pop();

        builder.push("fire_cloud");
        FIRE_CLOUD_MAX_PARTICLES_PER_TICK = builder
                .defineInRange("max_particles_per_tick", 48, 1, 512);
        FIRE_CLOUD_PARTICLE_DENSITY = builder
                .defineInRange("particle_density", 0.62D, 0.01D, 8.0D);
        FIRE_CLOUD_OVERLAP_DEDUPLICATION = builder
                .define("overlap_deduplication", true);
        builder.pop();
        return builder.build();
    }
}
