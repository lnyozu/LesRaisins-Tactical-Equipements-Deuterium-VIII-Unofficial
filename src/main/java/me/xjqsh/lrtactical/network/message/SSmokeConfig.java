package me.xjqsh.lrtactical.network.message;

import me.xjqsh.lrtactical.client.smoke.ClientSmokeManager;
import me.xjqsh.lrtactical.entity.sp.SpEffectCloudEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SSmokeConfig(
        int smokeShape,
        double radius,
        double height,
        int fireMaxCells,
        int fireStepUp,
        int fireDropDown,
        int fireCacheRefreshTicks
) {
    public static void encode(SSmokeConfig message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.smokeShape);
        buffer.writeDouble(message.radius);
        buffer.writeDouble(message.height);
        buffer.writeVarInt(message.fireMaxCells);
        buffer.writeVarInt(message.fireStepUp);
        buffer.writeVarInt(message.fireDropDown);
        buffer.writeVarInt(message.fireCacheRefreshTicks);
    }

    public static SSmokeConfig decode(FriendlyByteBuf buffer) {
        return new SSmokeConfig(
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public static void handle(
            SSmokeConfig message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                ClientSmokeManager.applyServerConfig(
                        message.smokeShape,
                        message.radius,
                        message.height
                );
                SpEffectCloudEntity.applyClientServerConfig(
                        message.fireMaxCells,
                        message.fireStepUp,
                        message.fireDropDown,
                        message.fireCacheRefreshTicks
                );
            });
        }
        context.setPacketHandled(true);
    }
}
