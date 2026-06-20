package me.xjqsh.lrtactical.client.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.xjqsh.lrtactical.item.throwable.smoke.SmokeSurfaceData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Closed radial low-poly shell used by the solid anime smoke renderer.
 *
 * <p>The previous marching-tetrahedra implementation could generate tens of
 * thousands of tiny triangles and had to stop at a face cap, leaving visible
 * holes. This mesh starts from a closed icosphere and only changes each
 * vertex's radius, so it remains watertight and produces broad anime facets.</p>
 */
final class SolidAnimeSmokeMesh {
    static final SolidAnimeSmokeMesh EMPTY = new SolidAnimeSmokeMesh(List.of());

    private static final int ICOSPHERE_SUBDIVISIONS = 2;
    private static final double MIN_RADIUS = 0.55D;
    private static final Point LIGHT_DIRECTION =
            new Point(-0.32D, 0.86D, -0.39D).normalize();
    private static final Point HIGHLIGHT_BAND_A =
            new Point(0.74D, 0.31D, -0.60D).normalize();
    private static final Point HIGHLIGHT_BAND_B =
            new Point(-0.41D, 0.83D, 0.38D).normalize();
    private static final Point HIGHLIGHT_BAND_C =
            new Point(0.22D, 0.47D, 0.86D).normalize();

    private final List<Face> faces;

    private SolidAnimeSmokeMesh(List<Face> faces) {
        this.faces = faces;
    }

    static SolidAnimeSmokeMesh build(
            ClientLevel level,
            Vec3 origin,
            Set<Long> occupiedCells,
            SmokeSurfaceData settings
    ) {
        if (occupiedCells.isEmpty()) {
            return EMPTY;
        }

        Icosphere topology = createIcosphere(ICOSPHERE_SUBDIVISIONS);
        double maximumRadius = calculateMaximumRadius(origin, occupiedCells)
                + settings.getSurfaceInfluence();
        double[] radii = new double[topology.directions.size()];
        for (int i = 0; i < topology.directions.size(); i++) {
            Point direction = topology.directions.get(i);
            radii[i] = traceRadius(
                    level,
                    origin,
                    direction,
                    occupiedCells,
                    settings,
                    maximumRadius
            );
            radii[i] *= coarseRadiusVariation(direction);
        }

        List<Set<Integer>> neighbours = buildNeighbours(
                topology.directions.size(),
                topology.triangles
        );
        smoothRadii(radii, neighbours, settings);
        limitNeighbourRadiusDifference(radii, neighbours);

        ArrayList<Point> vertices = new ArrayList<>(topology.directions.size());
        for (int i = 0; i < topology.directions.size(); i++) {
            Point direction = topology.directions.get(i);
            double radius = Math.max(MIN_RADIUS, radii[i]);
            vertices.add(new Point(
                    origin.x + direction.x * radius,
                    origin.y + direction.y * radius,
                    origin.z + direction.z * radius
            ));
        }

        ArrayList<Face> faces = new ArrayList<>(topology.triangles.size());
        Point originPoint = new Point(origin.x, origin.y, origin.z);
        for (int[] triangle : topology.triangles) {
            Point first = vertices.get(triangle[0]);
            Point second = vertices.get(triangle[1]);
            Point third = vertices.get(triangle[2]);
            Point normal = second.subtract(first).cross(third.subtract(first));
            Point faceCenter = first.add(second).add(third).scale(1.0D / 3.0D);
            if (normal.dot(faceCenter.subtract(originPoint)) < 0.0D) {
                Point temporary = second;
                second = third;
                third = temporary;
                normal = second.subtract(first).cross(third.subtract(first));
            }
            normal = normal.normalize();
            if (normal.lengthSquared() < 1.0E-10D) {
                continue;
            }
            faces.add(new Face(
                    first,
                    second,
                    third,
                    selectColor(
                            normal,
                            faceCenter.subtract(originPoint).normalize(),
                            settings
                    )
            ));
        }
        return faces.isEmpty() ? EMPTY : new SolidAnimeSmokeMesh(List.copyOf(faces));
    }

    boolean isEmpty() {
        return faces.isEmpty();
    }

    void render(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Vec3 cameraPosition,
            Vec3 expansionOrigin,
            double expansionScale
    ) {
        for (Face face : faces) {
            emitVertex(
                    pose,
                    consumer,
                    face.a.scaleFrom(expansionOrigin, expansionScale),
                    cameraPosition,
                    face.color
            );
            emitVertex(
                    pose,
                    consumer,
                    face.b.scaleFrom(expansionOrigin, expansionScale),
                    cameraPosition,
                    face.color
            );
            emitVertex(
                    pose,
                    consumer,
                    face.c.scaleFrom(expansionOrigin, expansionScale),
                    cameraPosition,
                    face.color
            );
        }
    }

    private static void emitVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Point point,
            Vec3 cameraPosition,
            int color
    ) {
        consumer.vertex(
                        pose.pose(),
                        (float) (point.x - cameraPosition.x),
                        (float) (point.y - cameraPosition.y),
                        (float) (point.z - cameraPosition.z)
                )
                .color(
                        color >> 16 & 0xFF,
                        color >> 8 & 0xFF,
                        color & 0xFF,
                        255
                )
                .endVertex();
    }

    private static double calculateMaximumRadius(
            Vec3 origin,
            Set<Long> occupiedCells
    ) {
        double maximum = MIN_RADIUS;
        for (long key : occupiedCells) {
            maximum = Math.max(
                    maximum,
                    BlockPos.of(key).getCenter().distanceTo(origin)
            );
        }
        return maximum;
    }

    private static double traceRadius(
            ClientLevel level,
            Vec3 origin,
            Point direction,
            Set<Long> occupiedCells,
            SmokeSurfaceData settings,
            double maximumRadius
    ) {
        double step = Math.max(0.16D, Math.min(0.32D, settings.getResolution() * 0.30D));
        double lastInside = MIN_RADIUS;
        boolean enteredSmoke = false;
        int outsideSamples = 0;
        for (double distance = 0.0D; distance <= maximumRadius; distance += step) {
            double x = origin.x + direction.x * distance;
            double y = origin.y + direction.y * distance;
            double z = origin.z + direction.z * distance;
            double density = sampleDensity(
                    occupiedCells,
                    x,
                    y,
                    z,
                    settings.getSurfaceInfluence()
            );
            if (density >= settings.getDensityThreshold()) {
                enteredSmoke = true;
                outsideSamples = 0;
                lastInside = distance;
            } else if (enteredSmoke && ++outsideSamples >= 2) {
                break;
            }
        }
        return Math.max(MIN_RADIUS, lastInside + step * 0.65D);
    }

    private static double sampleDensity(
            Set<Long> occupiedCells,
            double x,
            double y,
            double z,
            double influence
    ) {
        BlockPos sampleBlock = BlockPos.containing(x, y, z);
        double influenceSquared = influence * influence;
        double density = 0.0D;
        int searchRadius = Math.max(1, (int) Math.ceil(influence));
        for (int offsetY = -searchRadius; offsetY <= searchRadius; offsetY++) {
            for (int offsetZ = -searchRadius; offsetZ <= searchRadius; offsetZ++) {
                for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
                    int cellX = sampleBlock.getX() + offsetX;
                    int cellY = sampleBlock.getY() + offsetY;
                    int cellZ = sampleBlock.getZ() + offsetZ;
                    if (!occupiedCells.contains(BlockPos.asLong(cellX, cellY, cellZ))) {
                        continue;
                    }
                    double deltaX = x - (cellX + 0.5D);
                    double deltaY = y - (cellY + 0.5D);
                    double deltaZ = z - (cellZ + 0.5D);
                    double distanceSquared =
                            deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                    if (distanceSquared >= influenceSquared) {
                        continue;
                    }
                    double kernel = 1.0D - distanceSquared / influenceSquared;
                    density += kernel * kernel;
                }
            }
        }
        return density;
    }

    private static double coarseRadiusVariation(Point direction) {
        double wave = Math.sin(direction.x * 5.1D + direction.y * 2.7D)
                + Math.sin(direction.z * 4.3D - direction.y * 3.2D)
                + Math.sin((direction.x + direction.z) * 3.6D);
        return 1.0D + wave * 0.018D;
    }

    private static void smoothRadii(
            double[] radii,
            List<Set<Integer>> neighbours,
            SmokeSurfaceData settings
    ) {
        int iterations = Math.max(1, settings.getSmoothingIterations());
        double strength = Math.max(0.22D, settings.getSmoothingStrength());
        for (int iteration = 0; iteration < iterations; iteration++) {
            double[] next = radii.clone();
            for (int i = 0; i < radii.length; i++) {
                Set<Integer> adjacent = neighbours.get(i);
                if (adjacent.isEmpty()) {
                    continue;
                }
                double average = 0.0D;
                for (int neighbour : adjacent) {
                    average += radii[neighbour];
                }
                average /= adjacent.size();
                next[i] = radii[i] + (average - radii[i]) * strength;
            }
            System.arraycopy(next, 0, radii, 0, radii.length);
        }
    }

    private static void limitNeighbourRadiusDifference(
            double[] radii,
            List<Set<Integer>> neighbours
    ) {
        for (int iteration = 0; iteration < 4; iteration++) {
            double[] next = radii.clone();
            for (int i = 0; i < radii.length; i++) {
                Set<Integer> adjacent = neighbours.get(i);
                if (adjacent.isEmpty()) {
                    continue;
                }
                double average = 0.0D;
                for (int neighbour : adjacent) {
                    average += radii[neighbour];
                }
                average /= adjacent.size();
                double allowedDifference = Math.max(0.52D, average * 0.16D);
                next[i] = Math.max(
                        average - allowedDifference,
                        Math.min(average + allowedDifference, radii[i])
                );
            }
            System.arraycopy(next, 0, radii, 0, radii.length);
        }
    }

    private static int selectColor(
            Point normal,
            Point radialDirection,
            SmokeSurfaceData settings
    ) {
        double light = normal.dot(LIGHT_DIRECTION);
        double pattern = lowFrequencyColorPattern(radialDirection);
        double highlight = Math.max(
                sphericalBand(radialDirection, HIGHLIGHT_BAND_A, 0.12D, 0.145D),
                Math.max(
                        sphericalBand(radialDirection, HIGHLIGHT_BAND_B, -0.28D, 0.120D),
                        sphericalBand(radialDirection, HIGHLIGHT_BAND_C, 0.42D, 0.105D)
                )
        );
        double upperSurface = smoothStep((radialDirection.y + 0.30D) / 0.95D);
        double highlightSignal = highlight
                * (0.64D + upperSurface * 0.36D)
                + Math.max(0.0D, light - 0.48D) * 0.24D;
        if (highlightSignal >= 0.60D) {
            return settings.getLightColor();
        }

        double lowerSurface = smoothStep((-radialDirection.y - 0.02D) / 0.82D);
        double shadowSignal = -pattern * 0.58D
                + Math.max(0.0D, -light - 0.04D) * 0.54D
                + lowerSurface * 0.22D;
        if (shadowSignal >= 0.58D) {
            return settings.getShadowColor();
        }
        return settings.getBaseColor();
    }

    private static double lowFrequencyColorPattern(Point direction) {
        double first = Math.sin(
                direction.x * 3.15D
                        + direction.y * 1.65D
                        - direction.z * 2.45D
                        + 0.65D
        );
        double second = Math.sin(
                -direction.x * 1.90D
                        + direction.y * 3.10D
                        + direction.z * 2.25D
                        - 1.10D
        );
        double third = Math.sin(
                (direction.x + direction.z) * 3.65D
                        - direction.y * 1.80D
                        + 2.05D
        );
        return first * 0.52D + second * 0.31D + third * 0.17D;
    }

    private static double sphericalBand(
            Point direction,
            Point axis,
            double offset,
            double halfWidth
    ) {
        double distance = Math.abs(direction.dot(axis) - offset);
        return 1.0D - smoothStep(distance / halfWidth);
    }

    private static double smoothStep(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static Icosphere createIcosphere(int subdivisions) {
        double goldenRatio = (1.0D + Math.sqrt(5.0D)) * 0.5D;
        ArrayList<Point> directions = new ArrayList<>(List.of(
                new Point(-1, goldenRatio, 0).normalize(),
                new Point(1, goldenRatio, 0).normalize(),
                new Point(-1, -goldenRatio, 0).normalize(),
                new Point(1, -goldenRatio, 0).normalize(),
                new Point(0, -1, goldenRatio).normalize(),
                new Point(0, 1, goldenRatio).normalize(),
                new Point(0, -1, -goldenRatio).normalize(),
                new Point(0, 1, -goldenRatio).normalize(),
                new Point(goldenRatio, 0, -1).normalize(),
                new Point(goldenRatio, 0, 1).normalize(),
                new Point(-goldenRatio, 0, -1).normalize(),
                new Point(-goldenRatio, 0, 1).normalize()
        ));
        List<int[]> triangles = new ArrayList<>(List.of(
                new int[]{0, 11, 5}, new int[]{0, 5, 1},
                new int[]{0, 1, 7}, new int[]{0, 7, 10},
                new int[]{0, 10, 11}, new int[]{1, 5, 9},
                new int[]{5, 11, 4}, new int[]{11, 10, 2},
                new int[]{10, 7, 6}, new int[]{7, 1, 8},
                new int[]{3, 9, 4}, new int[]{3, 4, 2},
                new int[]{3, 2, 6}, new int[]{3, 6, 8},
                new int[]{3, 8, 9}, new int[]{4, 9, 5},
                new int[]{2, 4, 11}, new int[]{6, 2, 10},
                new int[]{8, 6, 7}, new int[]{9, 8, 1}
        ));

        for (int subdivision = 0; subdivision < subdivisions; subdivision++) {
            Map<Long, Integer> midpointCache = new HashMap<>();
            ArrayList<int[]> refined = new ArrayList<>(triangles.size() * 4);
            for (int[] triangle : triangles) {
                int firstMiddle = midpoint(
                        triangle[0],
                        triangle[1],
                        directions,
                        midpointCache
                );
                int secondMiddle = midpoint(
                        triangle[1],
                        triangle[2],
                        directions,
                        midpointCache
                );
                int thirdMiddle = midpoint(
                        triangle[2],
                        triangle[0],
                        directions,
                        midpointCache
                );
                refined.add(new int[]{triangle[0], firstMiddle, thirdMiddle});
                refined.add(new int[]{triangle[1], secondMiddle, firstMiddle});
                refined.add(new int[]{triangle[2], thirdMiddle, secondMiddle});
                refined.add(new int[]{firstMiddle, secondMiddle, thirdMiddle});
            }
            triangles = refined;
        }
        return new Icosphere(List.copyOf(directions), List.copyOf(triangles));
    }

    private static int midpoint(
            int first,
            int second,
            List<Point> directions,
            Map<Long, Integer> cache
    ) {
        int minimum = Math.min(first, second);
        int maximum = Math.max(first, second);
        long key = ((long) minimum << 32) | (maximum & 0xFFFFFFFFL);
        Integer existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        Point midpoint = directions.get(first)
                .add(directions.get(second))
                .scale(0.5D)
                .normalize();
        int id = directions.size();
        directions.add(midpoint);
        cache.put(key, id);
        return id;
    }

    private static List<Set<Integer>> buildNeighbours(
            int vertexCount,
            List<int[]> triangles
    ) {
        ArrayList<Set<Integer>> neighbours = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            neighbours.add(new HashSet<>());
        }
        for (int[] triangle : triangles) {
            connect(neighbours, triangle[0], triangle[1]);
            connect(neighbours, triangle[1], triangle[2]);
            connect(neighbours, triangle[2], triangle[0]);
        }
        return neighbours;
    }

    private static void connect(
            List<Set<Integer>> neighbours,
            int first,
            int second
    ) {
        neighbours.get(first).add(second);
        neighbours.get(second).add(first);
    }

    private record Icosphere(List<Point> directions, List<int[]> triangles) {
    }

    private record Face(Point a, Point b, Point c, int color) {
    }

    private record Point(double x, double y, double z) {
        private Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        private Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        private Point scale(double amount) {
            return new Point(x * amount, y * amount, z * amount);
        }

        private double dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }

        private Point cross(Point other) {
            return new Point(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        private double lengthSquared() {
            return dot(this);
        }

        private Point normalize() {
            double lengthSquared = lengthSquared();
            if (lengthSquared < 1.0E-12D) {
                return new Point(0.0D, 1.0D, 0.0D);
            }
            return scale(1.0D / Math.sqrt(lengthSquared));
        }

        private Point scaleFrom(Vec3 origin, double amount) {
            return new Point(
                    origin.x + (x - origin.x) * amount,
                    origin.y + (y - origin.y) * amount,
                    origin.z + (z - origin.z) * amount
            );
        }
    }
}
