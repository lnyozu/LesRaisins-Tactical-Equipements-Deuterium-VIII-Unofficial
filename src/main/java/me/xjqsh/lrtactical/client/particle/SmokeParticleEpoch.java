package me.xjqsh.lrtactical.client.particle;

public final class SmokeParticleEpoch {
    private static int epoch;

    private SmokeParticleEpoch() {
    }

    public static int current() {
        return epoch;
    }

    public static void clearExistingParticles() {
        epoch++;
    }
}
