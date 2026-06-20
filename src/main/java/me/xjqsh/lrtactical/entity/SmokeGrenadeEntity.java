package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.config.ServerConfig;
import me.xjqsh.lrtactical.server.smoke.ServerSmokeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.PlayMessages;
import org.jetbrains.annotations.Nullable;

public class SmokeGrenadeEntity extends ThrowableItemEntity {
    private static final double MOTION_SLEEP_HORIZONTAL_SPEED_SQR = 0.015D * 0.015D;
    private static final double SUPPORT_CHECK_DEPTH = 0.08D;

    private int lowMotionTicks;
    private boolean motionSleeping;

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
        super(TYPE, entity, level, ServerConfig.adjustSmokeLifetimeTicks(lifeTime));
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
        if (!this.level().isClientSide()) {
            boolean enteredMotionSleep = updateMotionSleepState();
            int remainingTicks = getRemainingSmokeTicks();
            if (this.level() instanceof ServerLevel serverLevel) {
                int smokeStartTime = ServerConfig.getSmokeStartDelayTicks();
                if (tickCount == smokeStartTime) {
                    ServerSmokeManager.register(
                            serverLevel,
                            this.getUUID(),
                            this.position(),
                            remainingTicks,
                            getThrowableIndexId()
                    );
                    playThrowableSound("release", 1.0f, 1.0f);
                } else if (tickCount > smokeStartTime && (enteredMotionSleep
                        || (!motionSleeping
                        && tickCount % ServerConfig.getSmokeMovingSyncIntervalTicks() == 0))) {
                    ServerSmokeManager.updatePosition(
                            serverLevel,
                            this.getUUID(),
                            this.position(),
                            remainingTicks,
                            getThrowableIndexId()
                    );
                }
            }
        }
    }

    @Override
    protected boolean shouldTickThrowableMotion() {
        return this.level().isClientSide() || !motionSleeping;
    }

    private boolean updateMotionSleepState() {
        if (motionSleeping) {
            return false;
        }

        Vec3 motion = this.getDeltaMovement();
        boolean lowMotion = motion.horizontalDistanceSqr() <= MOTION_SLEEP_HORIZONTAL_SPEED_SQR
                && Math.abs(motion.y) <= this.getGravity() + 0.01D;
        boolean supported = !this.level().noCollision(
                this,
                this.getBoundingBox().move(0.0D, -SUPPORT_CHECK_DEPTH, 0.0D)
        );
        if (!lowMotion || !supported) {
            lowMotionTicks = 0;
            return false;
        }

        if (++lowMotionTicks < ServerConfig.getSmokeMotionSleepConfirmTicks()) {
            return false;
        }

        motionSleeping = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        return true;
    }

    private int getRemainingSmokeTicks() {
        int cleanupRemaining = Math.max(0,
                ServerConfig.getThrowableForceCleanupTimeTicks() - this.tickCount);
        return this.getLife() > 0
                ? Math.min(Math.max(0, this.getLife() - this.tickCount), cleanupRemaining)
                : cleanupRemaining;
    }

    private ResourceLocation getThrowableIndexId() {
        var stack = this.getItem();
        if (stack.getItem() instanceof IThrowable throwable) {
            return throwable.getId(stack);
        }
        return IThrowable.EMPTY;
    }

    @Override
    public void onDeath(@Nullable HitResult hitResult) {
        if (this.level() instanceof ServerLevel serverLevel) {
            ServerSmokeManager.remove(serverLevel, this.getUUID());
        }
        super.onDeath(hitResult);
    }
}
