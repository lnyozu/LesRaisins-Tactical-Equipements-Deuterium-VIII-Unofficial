package me.xjqsh.lrtactical.network.message;

import me.xjqsh.lrtactical.client.particle.SmokeParticleEpoch;
import me.xjqsh.lrtactical.client.smoke.ClientSmokeManager;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SClientEffectControl(int flags) {
    public static final int CLEAR_SMOKE = 1;
    public static final int CLEAR_FIRE_CLOUDS = 1 << 1;

    public static void encode(SClientEffectControl message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.flags);
    }

    public static SClientEffectControl decode(FriendlyByteBuf buffer) {
        return new SClientEffectControl(buffer.readVarInt());
    }

    public static void handle(
            SClientEffectControl message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                if ((message.flags & CLEAR_SMOKE) != 0) {
                    ClientSmokeManager.clearAll();
                    SmokeParticleEpoch.clearExistingParticles();
                }
                if ((message.flags & CLEAR_FIRE_CLOUDS) != 0) {
                    SpEffectCloudEntity.clearClientVisualState();
                }
            });
        }
        context.setPacketHandled(true);
    }
}
