package me.xjqsh.lrtactical.client.resource;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientContentAvailability {
    private ClientContentAvailability() {
    }

    public static boolean hasConsumableDisplay(ResourceLocation id) {
        return LrTacticalAPI.getConsumableDisplay(id).isPresent();
    }

    public static boolean hasThrowableDisplay(ResourceLocation id) {
        return LrTacticalAPI.getThrowableDisplay(id).isPresent();
    }

    public static boolean hasMeleeDisplay(ResourceLocation id) {
        return LrTacticalAPI.getMeleeDisplay(id).isPresent();
    }

    public static boolean hasResource(ResourceLocation id) {
        return Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
    }
}
