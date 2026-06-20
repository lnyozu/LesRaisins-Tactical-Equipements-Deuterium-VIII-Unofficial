package me.xjqsh.lrtactical.item.throwable.smoke;

import java.util.Locale;

public enum SmokeRenderMode {
    PARTICLE(0, "particle"),
    SOLID_ANIME(1, "solid_anime");

    private final int networkId;
    private final String configName;

    SmokeRenderMode(int networkId, String configName) {
        this.networkId = networkId;
        this.configName = configName;
    }

    public int networkId() {
        return networkId;
    }

    public String configName() {
        return configName;
    }

    public static SmokeRenderMode fromConfig(String value) {
        if (value == null) {
            return PARTICLE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (SmokeRenderMode mode : values()) {
            if (mode.configName.equals(normalized)) {
                return mode;
            }
        }
        return PARTICLE;
    }

    public static SmokeRenderMode fromNetworkId(int networkId) {
        for (SmokeRenderMode mode : values()) {
            if (mode.networkId == networkId) {
                return mode;
            }
        }
        return PARTICLE;
    }
}
