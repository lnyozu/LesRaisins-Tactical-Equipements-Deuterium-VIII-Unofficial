package me.xjqsh.lrtactical.client.smoke;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.config.ClientConfig;
import me.xjqsh.lrtactical.config.SmokeShape;
import me.xjqsh.lrtactical.init.ModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = EquipmentMod.MOD_ID)
public final class ClientSmokeManager {
    private static final double SMOKE_SPREAD_DISTANCE_PER_TICK = 0.22D;
    private static final double INITIAL_EXPANSION_PROGRESS = 0.04D;
    private static final double VOLUME_REBUILD_DISTANCE_SQR = 0.75D * 0.75D;
    private static final double POSITION_SMOOTHING = 0.38D;
    private static final double POSITION_SNAP_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double SMOKE_EXPANSION_EDGE_WIDTH = 2.25D;
    private static final double MIN_SMOKE_CELL_WEIGHT = 0.02D;
    private static final double OCCLUDED_BASE_WEIGHT = 0.18D;
    private static final double OCCLUDED_STEP_FALLOFF = 0.65D;
    private static final double DISTANCE_STEP_FALLOFF = 0.88D;
    private static final double BODY_HEIGHT_SMOKE_BOOST = 1.45D;
    private static final double UPPER_SMOKE_WEIGHT = 0.68D;
    private static final Map<UUID, ActiveSmoke> ACTIVE_SMOKES = new LinkedHashMap<>();
    private static final LongOpenHashSet CLAIMED_SMOKE_CELLS = new LongOpenHashSet();

    private static ClientLevel trackedLevel;
    private static int clientTicks;
    private static SmokeShape smokeShape = SmokeShape.SPHERE;
    private static double smokeRadius = 5.5D;
    private static double smokeHeight = 4.5D;

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
                return new ActiveSmoke(position, remainingTicks, clientTicks);
            }
            if (existing.targetPosition.distanceToSqr(position) > VOLUME_REBUILD_DISTANCE_SQR) {
                existing.volumeDirty = true;
            }
            existing.targetPosition = position;
            existing.remainingTicks = remainingTicks;
            return existing;
        });
    }

    public static void remove(UUID id) {
        ACTIVE_SMOKES.remove(id);
    }

    public static void applyServerConfig(int shapeId, double radius, double height) {
        smokeShape = SmokeShape.fromNetworkId(shapeId);
        smokeRadius = Math.max(0.5D, radius);
        smokeHeight = Math.max(0.5D, height);
        for (ActiveSmoke smoke : ACTIVE_SMOKES.values()) {
            smoke.volumeDirty = true;
            smoke.invalidateSamplingTable();
        }
    }

    public static void clearAll() {
        clear();
    }

    public static void invalidateAllVolumes() {
        for (ActiveSmoke smoke : ACTIVE_SMOKES.values()) {
            smoke.volumeDirty = true;
            smoke.invalidateSamplingTable();
        }
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
        int particleBudget = ClientConfig.SMOKE_MAX_PARTICLES_PER_TICK.get();
        boolean deduplicateSmokeCells = ClientConfig.SMOKE_OVERLAP_DEDUPLICATION.get()
                && ACTIVE_SMOKES.size() > 1;
        CLAIMED_SMOKE_CELLS.clear();
        Iterator<ActiveSmoke> iterator = ACTIVE_SMOKES.values().iterator();
        while (iterator.hasNext()) {
            ActiveSmoke smoke = iterator.next();
            if (--smoke.remainingTicks <= 0) {
                iterator.remove();
                continue;
            }
            smoke.tickVisualPosition();

            double distanceSqr = player.position().distanceToSqr(smoke.position);
            double renderDistance = ClientConfig.SMOKE_RENDER_DISTANCE.get();
            if (distanceSqr > renderDistance * renderDistance || particleBudget <= 0) {
                continue;
            }

            smoke.ensureVolume(level, clientTicks);
            double expansion = smoke.getExpansionProgress(clientTicks);
            double uniqueCoverage = smoke.prepareSamplingTable(
                    expansion,
                    CLAIMED_SMOKE_CELLS,
                    deduplicateSmokeCells
            );
            if (uniqueCoverage <= 0.0D && smoke.hasVolume()) {
                continue;
            }
            int requested = Math.max(1, (int) Math.ceil(
                    particleCount(distanceSqr)
                            * (0.35D + 0.65D * expansion)
                            * uniqueCoverage
            ));
            int count = Math.min(requested, particleBudget);
            for (int i = 0; i < count; i++) {
                spawnParticle(level, smoke, expansion);
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
            return ClientConfig.SMOKE_NEAR_PARTICLES_PER_TICK.get();
        }
        if (distanceSqr <= 64.0D * 64.0D) {
            return ClientConfig.SMOKE_MEDIUM_PARTICLES_PER_TICK.get();
        }
        if (distanceSqr <= 96.0D * 96.0D) {
            return ClientConfig.SMOKE_FAR_PARTICLES_PER_TICK.get();
        }
        return clientTicks % 2 == 0 ? 2 : 1;
    }

    private static void spawnParticle(ClientLevel level, ActiveSmoke smoke, double expansion) {
        SmokeCell smokeCell = smoke.getRandomSmokeCell(level);
        if (smokeCell == null) {
            spawnFallbackParticle(level, smoke.position, expansion);
            return;
        }

        double targetX = smokeCell.pos().getX() + level.random.nextDouble();
        double targetY = smokeCell.pos().getY() + level.random.nextDouble();
        double targetZ = smokeCell.pos().getZ() + level.random.nextDouble();
        double reveal = smokeCell.directlyVisible()
                ? smoke.getCellReveal(smokeCell, expansion)
                : 1.0D;
        double spawnX = smoke.position.x + (targetX - smoke.position.x) * reveal;
        double spawnY = smoke.position.y + (targetY - smoke.position.y) * reveal;
        double spawnZ = smoke.position.z + (targetZ - smoke.position.z) * reveal;
        double velocityX = (targetX - spawnX) * 0.10D;
        double velocityY = (targetY - spawnY) * 0.10D;
        double velocityZ = (targetZ - spawnZ) * 0.10D;

        level.addParticle(ModParticleTypes.SMOKE_CLOUD.get(), true,
                spawnX, spawnY, spawnZ,
                velocityX, velocityY, velocityZ);
    }

    private static void spawnFallbackParticle(ClientLevel level, Vec3 center, double expansion) {
        double scale = Math.max(0.2D, expansion);
        double verticalExtent = smokeShape == SmokeShape.SPHERE ? smokeRadius : smokeHeight;
        for (int attempt = 0; attempt < 8; attempt++) {
            double offsetX = level.random.triangle(0.0D, smokeRadius * scale);
            double offsetY = level.random.triangle(0.0D, verticalExtent * scale);
            double offsetZ = level.random.triangle(0.0D, smokeRadius * scale);
            Vec3 point = center.add(offsetX, offsetY, offsetZ);
            if (!smokeShape.contains(
                    center,
                    point,
                    smokeRadius * scale,
                    smokeHeight * scale
            ) || !isSmokePassable(level, BlockPos.containing(point))) {
                continue;
            }
            level.addParticle(ModParticleTypes.SMOKE_CLOUD.get(), true,
                    point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            return;
        }
    }

    private static boolean isInsideSmoke(Entity entity) {
        for (ActiveSmoke smoke : ACTIVE_SMOKES.values()) {
            if (smoke.contains(entity)) {
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
        private Vec3 targetPosition;
        private int remainingTicks;
        private final int startTick;
        private Vec3 volumeCenter;
        private boolean volumeDirty = true;
        private int lastVolumeBuildTick = -3;
        private List<SmokeCell> smokeBlocks = Collections.emptyList();
        private Set<Long> smokeBlockKeys = Collections.emptySet();
        private Map<Long, Double> smokeBlockSpreadDistances = Collections.emptyMap();
        private double totalSmokeWeight;
        private double maxSmokeSpreadDistance;
        private int expansionTicks = 24;
        private boolean expansionDurationInitialized;
        private double[] cumulativeSamplingWeights = new double[0];
        private double samplingTotalWeight;
        private double cachedSamplingExpansion = Double.NaN;

        private ActiveSmoke(Vec3 position, int remainingTicks, int startTick) {
            this.position = position;
            this.targetPosition = position;
            this.remainingTicks = remainingTicks;
            this.startTick = startTick;
        }

        private void tickVisualPosition() {
            double distanceSqr = position.distanceToSqr(targetPosition);
            if (distanceSqr <= 1.0E-4D) {
                return;
            }

            if (distanceSqr > POSITION_SNAP_DISTANCE_SQR) {
                position = targetPosition;
                volumeDirty = true;
                return;
            }

            position = position.lerp(targetPosition, POSITION_SMOOTHING);
        }

        private void ensureVolume(ClientLevel level, int tick) {
            if (!volumeDirty && volumeCenter != null
                    && volumeCenter.distanceToSqr(position) <= VOLUME_REBUILD_DISTANCE_SQR) {
                return;
            }
            if (!smokeBlocks.isEmpty()
                    && tick - lastVolumeBuildTick
                    < ClientConfig.SMOKE_VOLUME_REBUILD_INTERVAL.get()) {
                return;
            }

            rebuildVolume(level);
            volumeCenter = position;
            volumeDirty = false;
            lastVolumeBuildTick = tick;
        }

        private void rebuildVolume(ClientLevel level) {
            BlockPos seed = findSeed(level, position);
            if (seed == null) {
                smokeBlocks = Collections.emptyList();
                smokeBlockKeys = Collections.emptySet();
                smokeBlockSpreadDistances = Collections.emptyMap();
                totalSmokeWeight = 0.0D;
                maxSmokeSpreadDistance = 0.0D;
                invalidateSamplingTable();
                return;
            }
            Vec3 visibilityOrigin = seed.getCenter();

            ArrayDeque<QueueEntry> queue = new ArrayDeque<>();
            Set<Long> visited = new HashSet<>();
            ArrayList<SmokeCell> blocks = new ArrayList<>();
            HashSet<Long> blockKeys = new HashSet<>();
            Map<Long, Double> blockSpreadDistances = new LinkedHashMap<>();
            double totalWeight = 0.0D;
            double maxSpreadDistance = 0.0D;

            queue.add(new QueueEntry(seed, 0, 0));
            visited.add(seed.asLong());

            while (!queue.isEmpty()
                    && blocks.size() < ClientConfig.SMOKE_MAX_VOLUME_CELLS.get()) {
                QueueEntry entry = queue.removeFirst();
                BlockPos current = entry.pos();
                if (!isInsideBounds(position, current) || !isSmokePassable(level, current)) {
                    continue;
                }

                boolean visible = hasSmokeLineOfSight(level, visibilityOrigin, current);
                int occludedDepth = visible ? 0 : Math.max(1, entry.occludedDepth());
                double weight = getSmokeCellWeight(position, current, entry.distance(), occludedDepth);
                if (weight < MIN_SMOKE_CELL_WEIGHT) {
                    continue;
                }

                double spreadDistance = getSmokeSpreadDistance(seed, current, entry.distance());
                blocks.add(new SmokeCell(current, weight, spreadDistance, visible));
                blockKeys.add(current.asLong());
                blockSpreadDistances.put(current.asLong(), spreadDistance);
                totalWeight += weight;
                maxSpreadDistance = Math.max(maxSpreadDistance, spreadDistance);

                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
                    long key = next.asLong();
                    if (visited.add(key) && isInsideBounds(position, next)) {
                        int nextOccludedDepth = visible ? 0 : occludedDepth + 1;
                        queue.addLast(new QueueEntry(next, entry.distance() + 1, nextOccludedDepth));
                    }
                }
            }

            smokeBlocks = List.copyOf(blocks);
            smokeBlockKeys = Set.copyOf(blockKeys);
            smokeBlockSpreadDistances = Map.copyOf(blockSpreadDistances);
            totalSmokeWeight = totalWeight;
            maxSmokeSpreadDistance = maxSpreadDistance;
            invalidateSamplingTable();
            initializeExpansionDuration(maxSpreadDistance);
        }

        private double getExpansionProgress(int tick) {
            double raw = Math.min(1.0D,
                    Math.max(0.0D, (tick - startTick + 1) / (double) expansionTicks));
            return INITIAL_EXPANSION_PROGRESS
                    + (1.0D - INITIAL_EXPANSION_PROGRESS) * smoothStep(raw);
        }

        private double prepareSamplingTable(
                double expansion,
                LongOpenHashSet claimedCells,
                boolean deduplicate
        ) {
            if (!deduplicate
                    && Double.compare(cachedSamplingExpansion, expansion) == 0
                    && cumulativeSamplingWeights.length == smokeBlocks.size()) {
                return 1.0D;
            }

            if (cumulativeSamplingWeights.length != smokeBlocks.size()) {
                cumulativeSamplingWeights = new double[smokeBlocks.size()];
            }

            double accumulated = 0.0D;
            double availableWeight = 0.0D;
            for (int i = 0; i < smokeBlocks.size(); i++) {
                SmokeCell smokeCell = smokeBlocks.get(i);
                double weight = getExpandedCellWeight(smokeCell, expansion);
                availableWeight += weight;
                if (weight > 0.0D
                        && (!deduplicate || claimedCells.add(smokeCell.pos().asLong()))) {
                    accumulated += weight;
                }
                cumulativeSamplingWeights[i] = accumulated;
            }
            samplingTotalWeight = accumulated;
            cachedSamplingExpansion = deduplicate ? Double.NaN : expansion;
            if (availableWeight <= 0.0D) {
                return smokeBlocks.isEmpty() ? 1.0D : 0.0D;
            }
            return accumulated / availableWeight;
        }

        private SmokeCell getRandomSmokeCell(ClientLevel level) {
            if (smokeBlocks.isEmpty() || totalSmokeWeight <= 0.0D) {
                return null;
            }
            if (samplingTotalWeight <= 0.0D) {
                return smokeBlocks.get(0);
            }

            double target = level.random.nextDouble() * samplingTotalWeight;
            int low = 0;
            int high = cumulativeSamplingWeights.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (cumulativeSamplingWeights[middle] <= target) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return smokeBlocks.get(Math.min(low, smokeBlocks.size() - 1));
        }

        private boolean contains(Entity entity) {
            if (smokeBlockKeys.isEmpty() || smokeBlockSpreadDistances.isEmpty()) {
                return isInsideFallbackShape(entity);
            }

            BlockPos feet = BlockPos.containing(entity.getX(), entity.getY() + 0.1D, entity.getZ());
            BlockPos eyes = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
            return isExpandedSmokeBlock(feet) || isExpandedSmokeBlock(eyes);
        }

        private boolean hasVolume() {
            return !smokeBlocks.isEmpty();
        }

        private double getExpandedCellWeight(SmokeCell smokeBlock, double expansion) {
            if (expansion >= 1.0D || maxSmokeSpreadDistance <= 0.0D) {
                return smokeBlock.weight();
            }

            double reveal = getCellReveal(smokeBlock, expansion);
            if (reveal <= 0.0D) {
                return 0.0D;
            }
            return smokeBlock.weight() * reveal;
        }

        private void invalidateSamplingTable() {
            cumulativeSamplingWeights = new double[0];
            samplingTotalWeight = 0.0D;
            cachedSamplingExpansion = Double.NaN;
        }

        private double getCellReveal(SmokeCell smokeBlock, double expansion) {
            if (expansion >= 1.0D || maxSmokeSpreadDistance <= 0.0D) {
                return 1.0D;
            }
            double visibleDistance = maxSmokeSpreadDistance * expansion;
            return smoothStep(
                    (visibleDistance - smokeBlock.spreadDistance())
                            / SMOKE_EXPANSION_EDGE_WIDTH + 1.0D
            );
        }

        private void initializeExpansionDuration(double spreadDistance) {
            if (expansionDurationInitialized || spreadDistance <= 0.0D) {
                return;
            }
            int calculatedTicks = (int) Math.ceil(
                    spreadDistance / SMOKE_SPREAD_DISTANCE_PER_TICK
            );
            int minimum = ClientConfig.SMOKE_MIN_EXPANSION_TICKS.get();
            int maximum = Math.max(minimum, ClientConfig.SMOKE_MAX_EXPANSION_TICKS.get());
            expansionTicks = Math.max(
                    minimum,
                    Math.min(maximum, calculatedTicks)
            );
            expansionDurationInitialized = true;
        }

        private boolean isExpandedSmokeBlock(BlockPos pos) {
            Double spreadDistance = smokeBlockSpreadDistances.get(pos.asLong());
            if (spreadDistance == null) {
                return false;
            }
            if (maxSmokeSpreadDistance <= 0.0D) {
                return true;
            }
            double visibleDistance = maxSmokeSpreadDistance * getExpansionProgress(clientTicks);
            return spreadDistance <= visibleDistance + SMOKE_EXPANSION_EDGE_WIDTH;
        }

        private boolean isInsideFallbackShape(Entity entity) {
            double minY = entity.getY();
            double maxY = minY + entity.getBbHeight();
            double closestY = Math.max(minY, Math.min(position.y, maxY));
            return smokeShape.contains(
                    position,
                    new Vec3(entity.getX(), closestY, entity.getZ()),
                    smokeRadius,
                    smokeHeight
            );
        }
    }

    private static BlockPos findSeed(ClientLevel level, Vec3 center) {
        BlockPos origin = BlockPos.containing(center);
        if (isInsideBounds(center, origin) && isSmokePassable(level, origin)) {
            return origin;
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int searchRadius = 2;
        for (int y = -searchRadius; y <= searchRadius; y++) {
            for (int x = -searchRadius; x <= searchRadius; x++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!isInsideBounds(center, candidate) || !isSmokePassable(level, candidate)) {
                        continue;
                    }
                    double distance = candidate.getCenter().distanceToSqr(center);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isInsideBounds(Vec3 center, BlockPos pos) {
        return smokeShape.contains(
                center,
                pos.getCenter(),
                smokeRadius,
                smokeHeight
        );
    }

    private static double getSmokeCellWeight(Vec3 center, BlockPos pos, int distance, int occludedDepth) {
        double distanceWeight = Math.pow(DISTANCE_STEP_FALLOFF, distance);
        double verticalWeight = getVerticalDensityWeight(center, pos);
        double baseWeight;
        if (occludedDepth <= 0) {
            baseWeight = distanceWeight;
        } else {
            baseWeight = OCCLUDED_BASE_WEIGHT
                    * Math.pow(OCCLUDED_STEP_FALLOFF, occludedDepth - 1)
                    * distanceWeight;
        }
        return baseWeight * verticalWeight;
    }

    private static double getSmokeSpreadDistance(BlockPos seed, BlockPos pos, int pathDistance) {
        double x = pos.getX() - seed.getX();
        double y = pos.getY() - seed.getY();
        double z = pos.getZ() - seed.getZ();
        double euclideanDistance = Math.sqrt(x * x + y * y + z * z);
        return pathDistance * 0.72D
                + euclideanDistance * 0.28D
                + getStableCellJitter(pos) * 0.85D;
    }

    private static double getStableCellJitter(BlockPos pos) {
        long hash = pos.asLong();
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return (hash & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private static double smoothStep(double value) {
        double clamped = Math.min(1.0D, Math.max(0.0D, value));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double getVerticalDensityWeight(Vec3 center, BlockPos pos) {
        double y = pos.getY() + 0.5D - center.y;
        if (y < -0.25D) {
            return 0.65D;
        }
        if (y <= 2.35D) {
            return BODY_HEIGHT_SMOKE_BOOST;
        }
        if (y <= 3.5D) {
            return 1.0D;
        }
        return UPPER_SMOKE_WEIGHT;
    }

    private static boolean isSmokePassable(ClientLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    private static boolean hasSmokeLineOfSight(ClientLevel level, Vec3 from, BlockPos targetPos) {
        Vec3 target = targetPos.getCenter();
        HitResult result = level.clip(new ClipContext(
                from,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null
        ));
        return result.getType() == HitResult.Type.MISS
                || result.getLocation().distanceToSqr(target) < 0.01D;
    }

    private record QueueEntry(BlockPos pos, int distance, int occludedDepth) {
    }

    private record SmokeCell(
            BlockPos pos,
            double weight,
            double spreadDistance,
            boolean directlyVisible
    ) {
    }
}
