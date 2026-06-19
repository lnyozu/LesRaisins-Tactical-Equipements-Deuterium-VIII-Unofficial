package me.xjqsh.lrtactical.network.message;

import me.xjqsh.lrtactical.mixin.client.CreativeModeTabsAccessor;
import me.xjqsh.lrtactical.network.DataType;
import me.xjqsh.lrtactical.resource.CommonNetworkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;


public record SPackSyncMessage(Map<DataType, Map<ResourceLocation, String>> cache) {

    public static void encode(SPackSyncMessage message, FriendlyByteBuf buf) {
        buf.writeMap(message.cache(), FriendlyByteBuf::writeEnum, (buf1, map) -> {
            buf1.writeMap(map, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeUtf);
        });
    }

    public static SPackSyncMessage decode(FriendlyByteBuf buf) {
        var map = buf.readMap(buf1 -> buf1.readEnum(DataType.class), buf2 -> {
            return buf2.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readUtf);
        });
        return new SPackSyncMessage(map);
    }

    public static void handle(SPackSyncMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> doSync(message));
        }
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void doSync(SPackSyncMessage message) {
        CommonNetworkCache.INSTANCE.fromNetwork(message.cache);
        // 外部 TACZ 包的索引在登录后才同步到客户端。清除创造标签页参数缓存，
        // 让当前打开的创造物品栏在下一 tick（或下次打开时）重新收集这些物品。
        CreativeModeTabsAccessor.lrtactical$setCachedParameters(null);
    }
}
