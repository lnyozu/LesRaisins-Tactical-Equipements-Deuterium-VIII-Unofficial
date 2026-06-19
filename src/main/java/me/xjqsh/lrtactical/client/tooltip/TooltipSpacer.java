package me.xjqsh.lrtactical.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record TooltipSpacer(int height) implements TooltipComponent {
    public static final int HALF_LINE_HEIGHT = 4;
    private static final String MARKER_TEXT = "\u0000lrtactical:half_line_spacer";

    public static Component marker() {
        return Component.literal(MARKER_TEXT);
    }

    public static boolean isMarker(FormattedText text) {
        return MARKER_TEXT.equals(text.getString());
    }
}
