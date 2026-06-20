package me.xjqsh.lrtactical.item.throwable;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.item.throwable.smoke.SmokeRenderMode;
import me.xjqsh.lrtactical.item.throwable.smoke.SmokeSurfaceData;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

// 投掷物属性配置
public class ThrowableData {
    @SerializedName("prepare_time")
    private int prepareTime = 10;
    
    @SerializedName("cookable")
    private boolean cookable = false;

    @SerializedName("initial_speed")
    private double initialSpeed = 1.5;

    @SerializedName("cooldown")
    private int cooldown = 40;

    @SerializedName("cooldown_category")
    private ResourceLocation cooldownCategory = null;

    @SerializedName("stack_size")
    private int stackSize = 1;

    @SerializedName("entity")
    private EntityData entityData = new EntityData();

    @SerializedName("put_away_time")
    private long putAwayTime = 0;

    @SerializedName("smoke_render_mode")
    private String smokeRenderMode = SmokeRenderMode.PARTICLE.configName();

    @SerializedName("smoke_surface")
    private SmokeSurfaceData smokeSurface = new SmokeSurfaceData();

    public int getPrepareTime() {
        return prepareTime;
    }

    public double getInitialSpeed() {
        return initialSpeed;
    }

    public int getCooldown() {
        return cooldown;
    }

    @Nullable
    public ResourceLocation getCooldownCategory() {
        return cooldownCategory;
    }

    public int getStackSize() {
        return stackSize;
    }

    public EntityData getEntityData() {
        return entityData;
    }

    public long getPutAwayTime() {
        return putAwayTime;
    }

    public boolean isCookable() {
        return cookable;
    }

    public SmokeRenderMode getSmokeRenderMode() {
        return SmokeRenderMode.fromConfig(smokeRenderMode);
    }

    public SmokeSurfaceData getSmokeSurface() {
        return smokeSurface == null ? new SmokeSurfaceData() : smokeSurface;
    }
}
