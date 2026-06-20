package me.xjqsh.lrtactical.entity;

import me.xjqsh.lrtactical.config.CommonConfig;
import me.xjqsh.lrtactical.network.NetworkHandler;
import me.xjqsh.lrtactical.network.message.SExplosionEffect;
import me.xjqsh.lrtactical.server.c4.C4ServerManager;
import me.xjqsh.lrtactical.util.CustomExplosion;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.PlayMessages;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class GrenadeEntity extends ThrowableItemEntity {
    public static EntityType<GrenadeEntity> TYPE = EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC)
            .setShouldReceiveVelocityUpdates(true)
            .setTrackingRange(64)
            .setUpdateInterval(1)
            .setCustomClientFactory(GrenadeEntity::new)
            .sized(0.3f, 0.3f)
            .noSave()
            .noSummon()
            .fireImmune()
            .build("grenade_entity");

    private double damage = 18.0;
    private float radius = 4.5f;
    private boolean destroyBlocks = false;
    private float destroyMultiplier = 1.0f;
    private double screenShakeTime = 20;
    private double screenShakeAmplitude = 50;
    private boolean triggerOnExplode = false;
    private boolean remoteDetonation = false;
    private boolean exploded = false; // 防止循环调用
    private UUID deploymentOwnerUUID;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, LivingEntity entity, Level level, int lifeTime) {
        super(type, entity, level, lifeTime);
        this.deploymentOwnerUUID = entity.getUUID();
    }

    public GrenadeEntity(LivingEntity entity, Level level, int lifeTime) {
        super(TYPE, entity, level, lifeTime);
    }

    public GrenadeEntity(PlayMessages.SpawnEntity spawnEntity, Level level) {
        super(TYPE, level);
    }

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void onDeath(HitResult hitResult) {
        if (exploded || this.isRemoved()) {
            return;
        }
        exploded = true;
        Vec3 pos = hitResult == null ? this.position() : this.position().lerp(hitResult.getLocation(), 0.8);
        if (!this.level().isClientSide()) {
            var type = this.isDestroyBlocks() && CommonConfig.GRENADE_EXPLOSION_BLOCK_DAMAGE.get() ?
                    Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.KEEP;
            // 安全上限，防止恶意 datapack 导致服务器卡死
            float clampedRadius = clampFinite(this.getRadius(), 0.0F, 64.0F, 4.5F);
            double clampedDamage = clampFinite(this.getDamage(), 0.0D, 500.0D, 18.0D);
            float clampedDestroy = clampFinite(this.destroyMultiplier, 0.0F, 5.0F, 1.0F);
            CustomExplosion explosion = new CustomExplosion(this.level(), this, clampedDamage, clampedRadius, type);
            explosion.setScreenShakeAmplitude(clampFinite(this.screenShakeAmplitude, 0.0D, 200.0D, 50.0D));
            explosion.setScreenShakeTime(clampFinite(this.screenShakeTime, 0.0D, 60.0D, 20.0D));
            explosion.setDestroyMultiplier(clampedDestroy);
            if (ForgeEventFactory.onExplosionStart(level(), explosion)) {
                // 插件或其他模组取消爆炸时也必须移除实体，避免产生永久哑弹。
                super.onDeath(hitResult);
                return;
            }
            explosion.explode();
            // 服务端只处理伤害和方块；客户端收到一个小型效果包后自行生成视觉效果。
            explosion.finalizeExplosion(false);
            NetworkHandler.sendToNearbyPlayers(
                    new SExplosionEffect(pos, clampedRadius, type != Explosion.BlockInteraction.KEEP),
                    this.level(), pos, 96.0D
            );
        }
        super.onDeath(hitResult);
    }

    private static float clampFinite(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(value, max)) : fallback;
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(value, max)) : fallback;
    }

    public boolean tryRemoteDetonate() {
        if (this.level().isClientSide() || !this.remoteDetonation || this.exploded || this.isRemoved()) {
            return false;
        }
        this.onDeath(null);
        return true;
    }

    public boolean tryQueuedExplosionDetonate() {
        if (this.level().isClientSide() || this.exploded || this.isRemoved()) {
            return false;
        }
        this.onDeath(null);
        return true;
    }

    @Override
    protected int getForceCleanupTimeTicks() {
        return this.remoteDetonation
                ? me.xjqsh.lrtactical.config.ServerConfig.getRemoteChargeForceCleanupTimeTicks()
                : super.getForceCleanupTimeTicks();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("ExplosionDamage", this.damage);
        tag.putFloat("ExplosionRadius", this.radius);
        tag.putBoolean("DestroyBlocks", this.destroyBlocks);
        tag.putFloat("DestroyMultiplier", this.destroyMultiplier);
        tag.putDouble("ScreenShakeTime", this.screenShakeTime);
        tag.putDouble("ScreenShakeAmplitude", this.screenShakeAmplitude);
        tag.putBoolean("TriggerOnExplode", this.triggerOnExplode);
        tag.putBoolean("RemoteDetonation", this.remoteDetonation);
        if (this.deploymentOwnerUUID != null) {
            tag.putUUID("DeploymentOwner", this.deploymentOwnerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionDamage")) {
            this.damage = tag.getDouble("ExplosionDamage");
        }
        if (tag.contains("ExplosionRadius")) {
            this.radius = tag.getFloat("ExplosionRadius");
        }
        if (tag.contains("DestroyBlocks")) {
            this.destroyBlocks = tag.getBoolean("DestroyBlocks");
        }
        if (tag.contains("DestroyMultiplier")) {
            this.destroyMultiplier = tag.getFloat("DestroyMultiplier");
        }
        if (tag.contains("ScreenShakeTime")) {
            this.screenShakeTime = tag.getDouble("ScreenShakeTime");
        }
        if (tag.contains("ScreenShakeAmplitude")) {
            this.screenShakeAmplitude = tag.getDouble("ScreenShakeAmplitude");
        }
        if (tag.contains("TriggerOnExplode")) {
            this.triggerOnExplode = tag.getBoolean("TriggerOnExplode");
        }
        if (tag.contains("RemoteDetonation")) {
            this.remoteDetonation = tag.getBoolean("RemoteDetonation");
        }
        if (tag.hasUUID("DeploymentOwner")) {
            this.deploymentOwnerUUID = tag.getUUID("DeploymentOwner");
        } else if (tag.hasUUID("Owner")) {
            // Migrate C4 entities saved before DeploymentOwner was introduced.
            this.deploymentOwnerUUID = tag.getUUID("Owner");
        }
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        if (!this.level().isClientSide() && triggerOnExplode && pSource.is(DamageTypeTags.IS_EXPLOSION) && !exploded) {
            C4ServerManager.queueChainDetonation(this);
            return true;
        }
        return super.hurt(pSource, pAmount);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && reason.shouldDestroy() && this.remoteDetonation) {
            C4ServerManager.unregister(this);
        }
        super.remove(reason);
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public boolean isDestroyBlocks() {
        return destroyBlocks;
    }

    public void setDestroyBlocks(boolean destroyBlocks) {
        this.destroyBlocks = destroyBlocks;
    }

    public double getScreenShakeTime() {
        return screenShakeTime;
    }

    public void setScreenShakeTime(double screenShakeTime) {
        this.screenShakeTime = screenShakeTime;
    }

    public double getScreenShakeAmplitude() {
        return screenShakeAmplitude;
    }

    public void setScreenShakeAmplitude(double screenShakeAmplitude) {
        this.screenShakeAmplitude = screenShakeAmplitude;
    }

    public float getDestroyMultiplier() {
        return destroyMultiplier;
    }

    public void setExplodeDestroyMultiplier(float destroyMultiplier) {
        this.destroyMultiplier = destroyMultiplier;
    }

    public void setTriggerOnExplode(boolean triggerOnExplode) {
        this.triggerOnExplode = triggerOnExplode;
    }

    public boolean isTriggerOnExplode() {
        return triggerOnExplode;
    }

    public void setRemoteDetonation(boolean remoteDetonation) {
        this.remoteDetonation = remoteDetonation;
    }

    public boolean isRemoteDetonation() {
        return remoteDetonation;
    }

    public UUID getDeploymentOwnerUUID() {
        if (this.deploymentOwnerUUID == null && this.getOwner() != null) {
            this.deploymentOwnerUUID = this.getOwner().getUUID();
        }
        return this.deploymentOwnerUUID;
    }
}
