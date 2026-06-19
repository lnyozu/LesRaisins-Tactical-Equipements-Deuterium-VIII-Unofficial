package me.xjqsh.lrtactical.network.message;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SExplosionEffect(Vec3 position, float radius, boolean destroysBlocks) {
    public static void encode(SExplosionEffect message, FriendlyByteBuf buffer) {
        buffer.writeDouble(message.position.x);
        buffer.writeDouble(message.position.y);
        buffer.writeDouble(message.position.z);
        buffer.writeFloat(message.radius);
        buffer.writeBoolean(message.destroysBlocks);
    }

    public static SExplosionEffect decode(FriendlyByteBuf buffer) {
        return new SExplosionEffect(
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat(),
                buffer.readBoolean()
        );
    }

    public static void handle(SExplosionEffect message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()
                && Float.isFinite(message.radius)
                && message.radius >= 0.0F
                && message.radius <= 64.0F
                && Double.isFinite(message.position.x)
                && Double.isFinite(message.position.y)
                && Double.isFinite(message.position.z)) {
            context.enqueueWork(() -> handleClient(message));
        }
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(SExplosionEffect message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Vec3 pos = message.position;
        level.addParticle(ParticleTypes.FLASH, true,
                pos.x, pos.y + 0.5D, pos.z, 0.0D, 0.0D, 0.0D);
        level.addParticle(
                message.radius >= 2.0F || message.destroysBlocks
                        ? ParticleTypes.EXPLOSION_EMITTER
                        : ParticleTypes.EXPLOSION,
                true, pos.x, pos.y + 0.25D, pos.z, 0.0D, 0.0D, 0.0D
        );
    }
}
