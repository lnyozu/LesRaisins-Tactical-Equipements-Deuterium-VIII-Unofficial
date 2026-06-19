package me.xjqsh.lrtactical.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.item.GunTooltipPart;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TooltipHideFlags {
    public static final String INDEX_KEY = "hide_tooltip";

    private TooltipHideFlags() {
    }

    /**
     * 使用 TACZ 原生 HideFlags 位定义判断指定 Tooltip 区块是否可见。
     */
    public static boolean shouldShow(ItemStack stack, GunTooltipPart part) {
        return (GunTooltipPart.getHideFlags(stack) & part.getMask()) == 0;
    }

    /**
     * 读取索引中的 hide_tooltip。未配置时不向物品写入 HideFlags。
     */
    @Nullable
    public static Integer parse(JsonObject json) {
        if (!json.has(INDEX_KEY)) {
            return null;
        }

        JsonElement element = json.get(INDEX_KEY);
        if (element == null || element.isJsonNull()
                || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("\"" + INDEX_KEY + "\" must be a non-negative integer");
        }

        double raw = element.getAsDouble();
        int value = element.getAsInt();
        if (!Double.isFinite(raw) || raw != value || value < 0) {
            throw new JsonParseException("\"" + INDEX_KEY + "\" must be a non-negative integer");
        }
        return value;
    }

    /**
     * 将索引配置写入 TACZ/原版共用的 HideFlags NBT。
     */
    public static void writeToStack(ItemStack stack, @Nullable Integer hideFlags) {
        if (hideFlags != null) {
            GunTooltipPart.setHideFlags(stack, hideFlags);
        }
    }
}
