package me.xjqsh.lrtactical.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.xjqsh.lrtactical.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SmokeCloudParticle extends TextureSheetParticle {
    private static final float MIN_SCALE = 2.6F;
    private static final float RANDOM_SCALE = 1.2F;
    private static final float SCALE_IN_TICKS = 10.0F;
    private static final float INITIAL_SCALE = 0.55F;
    private static final int FORMATION_TICKS = 12;
    private static final int SPRITE_TIMELINE = 100;
    private static final int HOLD_SPRITE_TIME = 56;

    public static SmokeCloudParticleProvider provider(SpriteSet spriteSet) {
        return new SmokeCloudParticleProvider(spriteSet);
    }

    public static class SmokeCloudParticleProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public SmokeCloudParticleProvider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        public Particle createParticle(SimpleParticleType typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SmokeCloudParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }

    private final SpriteSet spriteSet;
    private final int clearEpoch;
    private final float fadeInTicks;
    private final float fadeOutTicks;

    protected SmokeCloudParticle(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.clearEpoch = SmokeParticleEpoch.current();
        this.fadeInTicks = ClientConfig.SMOKE_PARTICLE_FADE_IN_TICKS.get();
        this.fadeOutTicks = ClientConfig.SMOKE_PARTICLE_FADE_OUT_TICKS.get();
        this.quadSize *= MIN_SCALE + this.random.nextFloat() * RANDOM_SCALE;
        int minimumLifetime = ClientConfig.SMOKE_PARTICLE_MIN_LIFETIME.get();
        int maximumLifetime = Math.max(
                minimumLifetime,
                ClientConfig.SMOKE_PARTICLE_MAX_LIFETIME.get()
        );
        this.lifetime = minimumLifetime
                + this.random.nextInt(maximumLifetime - minimumLifetime + 1);
        this.gravity = 0f;
        this.hasPhysics = false;
        this.friction = 0.90F;
        this.xd = vx + (this.random.nextDouble() - 0.5D) * 0.01D;
        this.yd = vy + this.random.nextDouble() * 0.006D;
        this.zd = vz + (this.random.nextDouble() - 0.5D) * 0.01D;
        this.updateSprite();
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    @Override
    public void tick() {
        if (clearEpoch != SmokeParticleEpoch.current()) {
            this.remove();
            return;
        }
        super.tick();
        if (!this.removed && this.age <= FORMATION_TICKS) {
            this.updateSprite();
        }
    }

    @Override
    public void render(VertexConsumer pBuffer, Camera pRenderInfo, float pPartialTicks) {
        float previousAlpha = this.alpha;
        this.setAlpha(this.getVisualAlpha(this.age + pPartialTicks));
        super.render(pBuffer, pRenderInfo, pPartialTicks);
        this.setAlpha(previousAlpha);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = smoothStep(Math.min(1.0F, (this.age + partialTick) / SCALE_IN_TICKS));
        return super.getQuadSize(partialTick) * (INITIAL_SCALE + (1.0F - INITIAL_SCALE) * progress);
    }

    @Override
    public boolean shouldCull() {
        return true;
    }

    private void updateSprite() {
        float formation = smoothStep(Math.min(1.0F, this.age / (float) FORMATION_TICKS));
        int spriteTime = Math.round(HOLD_SPRITE_TIME * formation);
        this.setSprite(this.spriteSet.get(spriteTime, SPRITE_TIMELINE));
    }

    private float getVisualAlpha(float visualAge) {
        float fadeIn = fadeInTicks <= 0
                ? 1.0F
                : smoothStep(Math.min(1.0F, visualAge / fadeInTicks));
        float remaining = this.lifetime - visualAge;
        float fadeOut = fadeOutTicks <= 0
                ? 1.0F
                : smoothStep(Math.min(1.0F, Math.max(0.0F, remaining / fadeOutTicks)));
        return Math.min(fadeIn, fadeOut);
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }
}
