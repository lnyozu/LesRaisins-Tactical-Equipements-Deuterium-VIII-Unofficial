package me.xjqsh.lrtactical.config;

import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public enum SmokeShape {
    SPHERE(0, "sphere"),
    CYLINDER(1, "cylinder");

    private final int networkId;
    private final String configName;

    SmokeShape(int networkId, String configName) {
        this.networkId = networkId;
        this.configName = configName;
    }

    public int networkId() {
        return networkId;
    }

    public String configName() {
        return configName;
    }

    public boolean contains(Vec3 center, Vec3 point, double radius, double halfHeight) {
        double dx = point.x - center.x;
        double dy = point.y - center.y;
        double dz = point.z - center.z;
        if (this == SPHERE) {
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
        return Math.abs(dy) <= halfHeight
                && dx * dx + dz * dz <= radius * radius;
    }

    public boolean intersectsExplosion(
            Vec3 smokeCenter,
            Vec3 explosionCenter,
            double smokeRadius,
            double smokeHalfHeight,
            double explosionRadius
    ) {
        double dx = explosionCenter.x - smokeCenter.x;
        double dy = explosionCenter.y - smokeCenter.y;
        double dz = explosionCenter.z - smokeCenter.z;
        if (this == SPHERE) {
            double combinedRadius = smokeRadius + explosionRadius;
            return dx * dx + dy * dy + dz * dz <= combinedRadius * combinedRadius;
        }

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double horizontalExcess = Math.max(0.0D, horizontalDistance - smokeRadius);
        double verticalExcess = Math.max(0.0D, Math.abs(dy) - smokeHalfHeight);
        return horizontalExcess * horizontalExcess + verticalExcess * verticalExcess
                <= explosionRadius * explosionRadius;
    }

    public static SmokeShape fromConfig(Object value) {
        if (value instanceof String string) {
            String normalized = string.trim().toLowerCase(Locale.ROOT);
            if ("cylinder".equals(normalized) || "cylindrical".equals(normalized)) {
                return CYLINDER;
            }
        }
        return SPHERE;
    }

    public static boolean isValidConfigValue(Object value) {
        if (!(value instanceof String string)) {
            return false;
        }
        String normalized = string.trim().toLowerCase(Locale.ROOT);
        return "sphere".equals(normalized) || "cylinder".equals(normalized);
    }

    public static SmokeShape fromNetworkId(int id) {
        return id == CYLINDER.networkId ? CYLINDER : SPHERE;
    }
}
