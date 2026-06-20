package me.xjqsh.lrtactical.item.throwable.smoke;

import com.google.gson.annotations.SerializedName;

public class SmokeSurfaceData {
    private static final String DEFAULT_BASE_COLOR = "#CDD0D3";
    private static final String DEFAULT_LIGHT_COLOR = "#ECEEEF";
    private static final String DEFAULT_SHADOW_COLOR = "#ADB2B8";

    @SerializedName("base_color")
    private String baseColor = DEFAULT_BASE_COLOR;

    @SerializedName("light_color")
    private String lightColor = DEFAULT_LIGHT_COLOR;

    @SerializedName("shadow_color")
    private String shadowColor = DEFAULT_SHADOW_COLOR;

    @SerializedName("resolution")
    private double resolution = 0.72D;

    @SerializedName("rebuild_interval_ticks")
    private int rebuildIntervalTicks = 5;

    @SerializedName("surface_influence")
    private double surfaceInfluence = 1.72D;

    @SerializedName("density_threshold")
    private double densityThreshold = 0.38D;

    @SerializedName("smoothing_iterations")
    private int smoothingIterations = 2;

    @SerializedName("smoothing_strength")
    private double smoothingStrength = 0.34D;

    public int getBaseColor() {
        return parseColor(baseColor, 0xCDD0D3);
    }

    public int getLightColor() {
        return parseColor(lightColor, 0xECEEEF);
    }

    public int getShadowColor() {
        return parseColor(shadowColor, 0xADB2B8);
    }

    public double getResolution() {
        return clamp(resolution, 0.55D, 1.25D);
    }

    public int getRebuildIntervalTicks() {
        return Math.max(2, Math.min(20, rebuildIntervalTicks));
    }

    public double getSurfaceInfluence() {
        return clamp(surfaceInfluence, 1.05D, 1.85D);
    }

    public double getDensityThreshold() {
        return clamp(densityThreshold, 0.12D, 0.75D);
    }

    public int getSmoothingIterations() {
        return Math.max(0, Math.min(4, smoothingIterations));
    }

    public double getSmoothingStrength() {
        return clamp(smoothingStrength, 0.0D, 0.55D);
    }

    public int visualSignature() {
        int result = getBaseColor();
        result = 31 * result + getLightColor();
        result = 31 * result + getShadowColor();
        result = 31 * result + Double.hashCode(getResolution());
        result = 31 * result + getRebuildIntervalTicks();
        result = 31 * result + Double.hashCode(getSurfaceInfluence());
        result = 31 * result + Double.hashCode(getDensityThreshold());
        result = 31 * result + getSmoothingIterations();
        result = 31 * result + Double.hashCode(getSmoothingStrength());
        return result;
    }

    private static int parseColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
