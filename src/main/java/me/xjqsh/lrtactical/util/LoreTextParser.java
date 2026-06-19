package me.xjqsh.lrtactical.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoreTextParser {
    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Style DEFAULT_STYLE = Style.EMPTY
            .withColor(ChatFormatting.DARK_GRAY)
            .withItalic(false);

    private LoreTextParser() {
    }

    /**
     * 读取索引中的 tooltip。推荐使用 tooltip；lore 仅作为旧配置兼容别名。
     */
    public static List<String> parseTooltip(JsonObject json) {
        String key;
        if (json.has("tooltip")) {
            key = "tooltip";
        } else if (json.has("lore")) {
            key = "lore";
        } else {
            return List.of();
        }

        JsonElement tooltipElement = json.get(key);
        if (tooltipElement == null || tooltipElement.isJsonNull()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        if (tooltipElement.isJsonPrimitive() && tooltipElement.getAsJsonPrimitive().isString()) {
            addLine(lines, tooltipElement.getAsString());
            return List.copyOf(lines);
        }

        if (!tooltipElement.isJsonArray()) {
            throw new JsonParseException("\"" + key + "\" must be a string or an array of strings");
        }

        for (JsonElement line : tooltipElement.getAsJsonArray()) {
            if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Every \"" + key + "\" entry must be a string");
            }
            addLine(lines, line.getAsString());
        }
        return List.copyOf(lines);
    }

    /**
     * 将索引 tooltip 一次性写入物品的 display.Lore，之后可直接编辑物品 NBT。
     */
    public static void writeToStack(ItemStack stack, List<String> tooltip) {
        ListTag loreTag = new ListTag();
        for (String line : tooltip) {
            Component parsed = parse(line);
            if (parsed != null) {
                loreTag.add(StringTag.valueOf(Component.Serializer.toJson(parsed)));
            }
        }
        if (!loreTag.isEmpty()) {
            stack.getOrCreateTagElement(ItemStack.TAG_DISPLAY).put(ItemStack.TAG_LORE, loreTag);
        }
    }

    /**
     * 解析一行索引 lore。
     * <p>
     * 无 HEX 标记时按翻译键处理；找不到翻译时 Minecraft 会直接显示原字符串。
     * HEX 格式为 {@code &#RRGGBB文本}，颜色持续到下一个 HEX 标记。
     */
    @Nullable
    public static Component parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Matcher matcher = HEX_COLOR.matcher(raw);
        if (!matcher.find()) {
            return Component.translatable(raw).withStyle(DEFAULT_STYLE);
        }

        MutableComponent result = Component.empty().withStyle(DEFAULT_STYLE);
        int textStart = 0;
        TextColor activeColor = null;
        boolean hasVisibleText = false;

        do {
            if (matcher.start() > textStart) {
                String text = raw.substring(textStart, matcher.start());
                result.append(coloredLiteral(text, activeColor));
                hasVisibleText = true;
            }

            activeColor = TextColor.fromRgb(Integer.parseInt(matcher.group(1), 16));
            textStart = matcher.end();
        } while (matcher.find());

        if (textStart < raw.length()) {
            result.append(coloredLiteral(raw.substring(textStart), activeColor));
            hasVisibleText = true;
        }

        return hasVisibleText ? result : null;
    }

    private static Component coloredLiteral(String text, @Nullable TextColor color) {
        if (color == null) {
            return Component.literal(text);
        }
        return Component.literal(text)
                .withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static void addLine(List<String> lines, String line) {
        if (line != null && !line.isBlank()) {
            lines.add(line);
        }
    }
}
