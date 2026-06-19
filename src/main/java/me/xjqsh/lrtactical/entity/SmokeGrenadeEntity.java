package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.config.ServerConfig;
import me.xjqsh.lrtactical.server.smoke.ServerSmokeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.PlayMessages;
import org.jetbrains.annotations.Nullable;

public class SmokeGrenadeEntity extends ThrowableItemEntity {
    public static final int SMOKE_START_TIME = 40;
    public static final double SMOKE_RADIUS = 5.5;
    public static final double SMOKE_HEIGHT = 4.5;

    public static EntityType<SmokeGrenadeEntity> TYPE = EntityType.Builder.<SmokeGrenadeEntity>of(SmokeGrenadeEntity::new, MobCategory.MISC)
            .setShouldReceiveVelocityUpdates(true)
            .setTrackingRange(64)
            .setUpdateInterval(1)
            .setCustomClientFactory(SmokeGrenadeEntity::new)
            .sized(0.3f, 0.3f)
            .noSave()
            .noSummon()
            .fireImmune()
            .build("smoke_grenade_entity");

    public SmokeGrenadeEntity(LivingEntity entity, Level level, int lifeTime) {
        super(TYPE, entity, level, lifeTime);
    }

    public SmokeGrenadeEntity(PlayMessages.SpawnEntity spawnEntity, Level level) {
        super(TYPE, level);
    }

    public SmokeGrenadeEntity(EntityType<SmokeGrenadeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (!this.level().isClientSide() && tickCount == SMOKE_START_TIME) {
            int cleanupRemaining = Math.max(0,
                    ServerConfig.getThrowableForceCleanupTimeTicks() - this.tickCount);
            int remainingTicks = this.getLife() > 0
                    ? Math.min(Math.max(0, this.getLife() - this.tickCount), cleanupRemaining)
                    : cleanupRemaining;
            if (this.level() instanceof ServerLevel serverLevel) {
                ServerSmokeManager.register(serverLevel, this.getUUID(), this.position(), remainingTicks);
            }
            playThrowableSound("release", 1.0f, 1.0f);
        }
    }

    @Override
    public void onDeath(@Nullable HitResult hitResult) {
        if (this.level() instanceof ServerLevel serverLevel) {
            ServerSmokeManager.remove(serverLevel, this.getUUID());
        }
        super.onDeath(hitResult);
    }
}
