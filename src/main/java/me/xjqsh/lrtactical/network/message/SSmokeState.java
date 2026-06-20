package me.xjqsh.lrtactical.network.message;

import me.xjqsh.lrtactical.client.smoke.ClientSmokeManager;
import me.xjqsh.lrtactical.item.throwable.smoke.SmokeRenderMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SSmokeState(
        UUID id,
        Vec3 position,
        int remainingTicks,
        boolean removed,
        ResourceLocation throwableIndexId,
        SmokeRenderMode renderMode
) {
    private static final int MAX_ACCEPTED_REMAINING_TICKS = 20 * 60 * 60 * 24;
    public static void encode(SSmokeState message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.id);
        buffer.writeDouble(message.position.x);
        buffer.writeDouble(message.position.y);
        buffer.writeDouble(message.position.z);
        buffer.writeVarInt(message.remainingTicks);
        buffer.writeBoolean(message.removed);
        buffer.writeResourceLocation(message.throwableIndexId);
        buffer.writeVarInt(message.renderMode.networkId());
    }

    public static SSmokeState decode(FriendlyByteBuf buffer) {
        return new SSmokeState(
                buffer.readUUID(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readResourceLocation(),
                SmokeRenderMode.fromNetworkId(buffer.readVarInt())
        );
    }

    public static void handle(SSmokeState message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                if (message.removed) {
                    ClientSmokeManager.remove(message.id);
                } else if (message.remainingTicks > 0
                        && message.remainingTicks <= MAX_ACCEPTED_REMAINING_TICKS
                        && Double.isFinite(message.position.x)
                        && Double.isFinite(message.position.y)
                        && Double.isFinite(message.position.z)) {
                    ClientSmokeManager.update(
                            message.id,
                            message.position,
                            message.remainingTicks,
                            message.throwableIndexId,
                            message.renderMode
                    );
                }
            });
        }
        context.setPacketHandled(true);
    }
}
