package me.xjqsh.lrtactical.entity.sp;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.xjqsh.lrtactical.config.ClientConfig;
import me.xjqsh.lrtactical.config.ServerConfig;
import me.xjqsh.lrtactical.entity.SmokeGrenadeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpEffectCloudEntity extends AreaEffectCloud {
    private static final int MIN_AREA_REBUILD_INTERVAL = 5;
    private static final double PLANAR_AREA_RADIUS_EPSILON = 0.2D;
    private static final double FIRE_CELL_Y_OFFSET = 0.08D;
    private static final LongOpenHashSet CLAIMED_CLIENT_FIRE_CELLS = new LongOpenHashSet();

    private static Level claimedClientLevel;
    private static long claimedClientGameTime = Long.MIN_VALUE;
    private static int clientMaxFireCells = 192;
    private static int clientStepUp = 1;
    private static int clientDropDown = 3;
    private static int clientCacheRefreshTicks = 40;
    private static int clientConfigEpoch;
    private static int clientClearEpoch;

    public static EntityType<SpEffectCloudEntity> TYPE = EntityType.Builder.<SpEffectCloudEntity>of(SpEffectCloudEntity::new, MobCategory.MISC)
            .fireImmune()
            .sized(6.0F, 0.5F)
            .noSave()
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(Integer.MAX_VALUE)
            .build("sp_effect_cloud");

    private boolean ignite = false;
    private int igniteTime = 2;
    private boolean extinguishBySmoke = false;
    private final List<MobEffectInstance> customEffects = new ArrayList<>();
    private int lastPlanarAreaBuildTick = -40;
    private float lastPlanarAreaRadius = -1.0F;
    private BlockPos lastPlanarAreaOrigin;
    private List<BlockPos> fireCells = Collections.emptyList();
    private Set<Long> fireCellKeys = Collections.emptySet();
    private final ArrayList<BlockPos> renderableFireCells = new ArrayList<>(192);
    private int observedClientConfigEpoch = -1;
    private final int clientClearEpochAtCreation = clientClearEpoch;

    public SpEffectCloudEntity(EntityType<? extends AreaEffectCloud> type, Level level) {
        super(type, level);
    }

    public SpEffectCloudEntity(Level level, double x, double y, double z) {
        this(TYPE, level);
        this.setPos(x, y, z);
    }

    @Override
    public void addEffect(MobEffectInstance effect) {
        this.customEffects.add(new MobEffectInstance(effect));
    }

    @Override
    public void tick() {
        if (this.level().isClientSide()) {
            this.baseTick();
            tickClientParticles();
            return;
        }

        super.tick();
        if (this.isRemoved() || this.isWaiting()
                || tickCount % ServerConfig.getFireCloudEffectIntervalTicks() != 0) {
            return;
        }

        if (this.isExtinguishBySmoke()
                && ServerConfig.isFireCloudSmokeExtinguishEnabled()) {
            double horizontalRange = ServerConfig.getSmokeRadius();
            double verticalRange = ServerConfig.getSmokeShape()
                    == me.xjqsh.lrtactical.config.SmokeShape.SPHERE
                    ? horizontalRange
                    : ServerConfig.getSmokeHeight();
            List<SmokeGrenadeEntity> smokeGrenades =
                    this.level().getEntitiesOfClass(
                            SmokeGrenadeEntity.class,
                            this.getBoundingBox().inflate(
                                    horizontalRange,
                                    verticalRange,
                                    horizontalRange
                            )
                    );
            for (SmokeGrenadeEntity smokeGrenade : smokeGrenades) {
                if (shouldBeExtinguished(smokeGrenade)) {
                    this.discard();
                    return;
                }
            }
        }

        if (!this.isIgnite() && this.customEffects.isEmpty()) {
            return;
        }

        ensurePlanarArea();
        List<LivingEntity> livingEntities =
                this.level().getEntitiesOfClass(LivingEntity.class, getEffectSearchBox());
        for (LivingEntity livingEntity : livingEntities) {
            if (!isInsideCloud(livingEntity)) {
                continue;
            }

            if (!this.customEffects.isEmpty() && livingEntity.isAffectedByPotions()) {
                applyCustomEffects(livingEntity);
            }
            if (this.isIgnite() && !livingEntity.fireImmune()) {
                livingEntity.setSecondsOnFire(this.getIgniteTime());
            }
        }
    }

    public boolean shouldBeExtinguished(SmokeGrenadeEntity smokeGrenade) {
        return smokeGrenade.tickCount >= ServerConfig.getSmokeStartDelayTicks()
                && ServerConfig.isInsideSmokeShape(
                        smokeGrenade.position(),
                        this.position()
                );
    }

    private boolean isInsideCloud(LivingEntity entity) {
        if (this.fireCellKeys.isEmpty()) {
            return false;
        }

        BlockPos feetPos = BlockPos.containing(
                entity.getX(),
                entity.getBoundingBox().minY + 0.1D,
                entity.getZ()
        );
        if (this.fireCellKeys.contains(feetPos.asLong())) {
            return true;
        }
        return this.fireCellKeys.contains(feetPos.below().asLong());
    }

    private AABB getEffectSearchBox() {
        double radius = this.getRadius();
        int dropDown = getConfiguredDropDown();
        int stepUp = getConfiguredStepUp();
        return new AABB(
                this.getX() - radius,
                this.getY() - dropDown - 1.0D,
                this.getZ() - radius,
                this.getX() + radius,
                this.getY() + stepUp + 2.0D,
                this.getZ() + radius
        );
    }

    private void applyCustomEffects(LivingEntity entity) {
        Entity source = this.getOwner() != null ? this.getOwner() : this;
        for (MobEffectInstance effect : this.customEffects) {
            MobEffect mobEffect = effect.getEffect();
            if (mobEffect.isInstantenous()) {
                mobEffect.applyInstantenousEffect(this, this.getOwner(), entity, effect.getAmplifier(), 0.5D);
            } else {
                entity.addEffect(new MobEffectInstance(effect), source);
            }
        }
    }

    private void tickClientParticles() {
        if (this.isRemoved() || clientClearEpochAtCreation != clientClearEpoch) {
            return;
        }
        if (this.isWaiting() && this.random.nextBoolean()) {
            return;
        }

        ensurePlanarArea();
        if (this.fireCells.isEmpty()) {
            return;
        }

        List<BlockPos> particleCells = getParticleCells();
        if (particleCells.isEmpty()) {
            return;
        }

        ParticleOptions particle = this.getParticle();
        int count = getFireParticleCount(particleCells.size());
        for (int i = 0; i < count; i++) {
            BlockPos cell = particleCells.get(this.random.nextInt(particleCells.size()));
            double x = cell.getX() + this.random.nextDouble();
            double y = cell.getY() + FIRE_CELL_Y_OFFSET + this.random.nextDouble() * 0.18D;
            double z = cell.getZ() + this.random.nextDouble();
            double xSpeed = (0.5D - this.random.nextDouble()) * 0.15D;
            double ySpeed = 0.01D + this.random.nextDouble() * 0.02D;
            double zSpeed = (0.5D - this.random.nextDouble()) * 0.15D;
            this.level().addParticle(particle, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }

    private List<BlockPos> getParticleCells() {
        if (this.isWaiting()) {
            return this.fireCells;
        }

        if (!ClientConfig.FIRE_CLOUD_OVERLAP_DEDUPLICATION.get()) {
            return this.fireCells;
        }
        resetClientFireCellClaimsIfNeeded();
        renderableFireCells.clear();
        for (BlockPos cell : fireCells) {
            if (CLAIMED_CLIENT_FIRE_CELLS.add(cell.asLong())) {
                renderableFireCells.add(cell);
            }
        }
        return renderableFireCells;
    }

    private void resetClientFireCellClaimsIfNeeded() {
        long gameTime = this.level().getGameTime();
        if (claimedClientLevel == this.level() && claimedClientGameTime == gameTime) {
            return;
        }
        claimedClientLevel = this.level();
        claimedClientGameTime = gameTime;
        CLAIMED_CLIENT_FIRE_CELLS.clear();
    }

    private int getFireParticleCount(int cellCount) {
        if (this.isWaiting()) {
            return Math.min(4, cellCount);
        }
        return Math.min(ClientConfig.FIRE_CLOUD_MAX_PARTICLES_PER_TICK.get(),
                Math.max(4, (int) Math.ceil(
                        cellCount * ClientConfig.FIRE_CLOUD_PARTICLE_DENSITY.get()
                )));
    }

    private void ensurePlanarArea() {
        if (this.level().isClientSide()
                && observedClientConfigEpoch != clientConfigEpoch) {
            invalidateAreaCache();
            observedClientConfigEpoch = clientConfigEpoch;
        }
        float radius = getEffectivePlanarRadius();
        BlockPos origin = BlockPos.containing(this.position());
        int elapsed = this.tickCount - this.lastPlanarAreaBuildTick;
        if (elapsed < MIN_AREA_REBUILD_INTERVAL) {
            return;
        }

        boolean originChanged = !origin.equals(this.lastPlanarAreaOrigin);
        boolean radiusChanged =
                Math.abs(radius - this.lastPlanarAreaRadius) > PLANAR_AREA_RADIUS_EPSILON;
        boolean cacheExpired = elapsed >= getConfiguredCacheRefreshTicks();
        if (!originChanged && !radiusChanged && !cacheExpired) {
            return;
        }

        rebuildPlanarArea(radius);
        this.lastPlanarAreaBuildTick = this.tickCount;
        this.lastPlanarAreaRadius = radius;
        this.lastPlanarAreaOrigin = origin;
    }

    private float getEffectivePlanarRadius() {
        float radius = this.getRadius();
        return this.isWaiting() ? Math.min(radius, 0.75F) : radius;
    }

    private void rebuildPlanarArea(float radius) {
        BlockPos seed = findPlanarSeed(radius);
        if (seed == null) {
            this.fireCells = Collections.emptyList();
            this.fireCellKeys = Collections.emptySet();
            return;
        }

        ArrayDeque<PlanarEntry> queue = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();
        ArrayList<BlockPos> cells = new ArrayList<>();
        HashSet<Long> cellKeys = new HashSet<>();

        queue.add(new PlanarEntry(seed));
        visited.add(seed.asLong());

        while (!queue.isEmpty() && cells.size() < getConfiguredMaxCells()) {
            PlanarEntry entry = queue.removeFirst();
            BlockPos current = entry.pos();
            cells.add(current);
            cellKeys.add(current.asLong());

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = findConnectedFireCell(current, direction, radius);
                if (next != null && visited.add(next.asLong())) {
                    queue.addLast(new PlanarEntry(next));
                }
            }
        }

        this.fireCells = List.copyOf(cells);
        this.fireCellKeys = Set.copyOf(cellKeys);
    }

    private BlockPos findPlanarSeed(float radius) {
        BlockPos origin = BlockPos.containing(this.position());
        if (isInsidePlanarRadius(origin, radius) && isValidFireCell(origin)) {
            return origin;
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = getConfiguredStepUp();
             y >= -getConfiguredDropDown();
             y--) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!isInsidePlanarRadius(candidate, radius) || !isValidFireCell(candidate)) {
                        continue;
                    }
                    double distance = candidate.getCenter().distanceToSqr(this.position());
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private boolean isInsidePlanarRadius(BlockPos pos, float radius) {
        double x = pos.getX() + 0.5D - this.getX();
        double z = pos.getZ() + 0.5D - this.getZ();
        return x * x + z * z <= radius * radius;
    }

    private boolean isValidFireCell(BlockPos pos) {
        if (!this.level().getWorldBorder().isWithinBounds(pos) || !this.level().hasChunkAt(pos)) {
            return false;
        }

        BlockState state = this.level().getBlockState(pos);
        if (!state.getCollisionShape(this.level(), pos).isEmpty() || !this.level().getFluidState(pos).isEmpty()) {
            return false;
        }

        BlockPos below = pos.below();
        if (!this.level().getWorldBorder().isWithinBounds(below) || !this.level().hasChunkAt(below)) {
            return false;
        }

        return !this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty();
    }

    private BlockPos findConnectedFireCell(
            BlockPos current,
            Direction direction,
            float radius
    ) {
        BlockPos horizontal = current.relative(direction);
        int stepUp = getConfiguredStepUp();
        int dropDown = getConfiguredDropDown();
        for (int searchIndex = 0; searchIndex <= stepUp + dropDown; searchIndex++) {
            int yOffset;
            if (searchIndex == 0) {
                yOffset = 0;
            } else if (searchIndex <= stepUp) {
                yOffset = searchIndex;
            } else {
                yOffset = -(searchIndex - stepUp);
            }
            BlockPos candidate = horizontal.offset(0, yOffset, 0);
            if (isInsidePlanarRadius(candidate, radius)
                    && isValidFireCell(candidate)
                    && hasTerrainClearance(current, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean hasTerrainClearance(BlockPos from, BlockPos to) {
        int heightDifference = to.getY() - from.getY();
        if (heightDifference > getConfiguredStepUp()
                || heightDifference < -getConfiguredDropDown()) {
            return false;
        }

        if (heightDifference > 0) {
            return isPassableFireSpace(from.above());
        }
        if (heightDifference < 0) {
            for (int y = from.getY(); y >= to.getY(); y--) {
                if (!isPassableFireSpace(new BlockPos(to.getX(), y, to.getZ()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isPassableFireSpace(BlockPos pos) {
        if (!this.level().getWorldBorder().isWithinBounds(pos)
                || !this.level().hasChunkAt(pos)) {
            return false;
        }
        BlockState state = this.level().getBlockState(pos);
        return state.getCollisionShape(this.level(), pos).isEmpty()
                && this.level().getFluidState(pos).isEmpty();
    }

    private record PlanarEntry(BlockPos pos) {
    }

    public void invalidateAreaCache() {
        lastPlanarAreaBuildTick = Integer.MIN_VALUE / 2;
        lastPlanarAreaRadius = -1.0F;
        lastPlanarAreaOrigin = null;
    }

    public static void clearClientVisualState() {
        clientClearEpoch++;
        refreshClientVisualState();
    }

    public static void refreshClientVisualState() {
        CLAIMED_CLIENT_FIRE_CELLS.clear();
        claimedClientLevel = null;
        claimedClientGameTime = Long.MIN_VALUE;
    }

    public static void applyClientServerConfig(
            int maxCells,
            int stepUp,
            int dropDown,
            int cacheRefreshTicks
    ) {
        clientMaxFireCells = Math.max(16, maxCells);
        clientStepUp = Math.max(0, stepUp);
        clientDropDown = Math.max(0, dropDown);
        clientCacheRefreshTicks = Math.max(MIN_AREA_REBUILD_INTERVAL, cacheRefreshTicks);
        clientConfigEpoch++;
        refreshClientVisualState();
    }

    private int getConfiguredMaxCells() {
        return this.level().isClientSide()
                ? clientMaxFireCells
                : ServerConfig.getFireCloudMaxCells();
    }

    private int getConfiguredStepUp() {
        return this.level().isClientSide()
                ? clientStepUp
                : ServerConfig.getFireCloudStepUp();
    }

    private int getConfiguredDropDown() {
        return this.level().isClientSide()
                ? clientDropDown
                : ServerConfig.getFireCloudDropDown();
    }

    private int getConfiguredCacheRefreshTicks() {
        return this.level().isClientSide()
                ? clientCacheRefreshTicks
                : ServerConfig.getFireCloudCacheRefreshTicks();
    }

    @NotNull
    public EntityDimensions getDimensions(@NotNull Pose pPose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
    }

    public boolean isIgnite() {
        return ignite;
    }

    public void setIgnite(boolean ignite) {
        this.ignite = ignite;
    }

    public boolean isExtinguishBySmoke() {
        return extinguishBySmoke;
    }

    public void setExtinguishBySmoke(boolean extinguishBySmoke) {
        this.extinguishBySmoke = extinguishBySmoke;
    }

    public int getIgniteTime() {
        return igniteTime;
    }

    public void setIgniteTime(int igniteTime) {
        this.igniteTime = igniteTime;
    }
}
