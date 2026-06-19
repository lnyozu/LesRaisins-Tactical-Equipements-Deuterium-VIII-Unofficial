package me.xjqsh.lrtactical.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public record ClientTooltipSpacer(int height) implements ClientTooltipComponent {
    public ClientTooltipSpacer(TooltipSpacer spacer) {
        this(spacer.height());
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth(Font font) {
        return 0;
    }
}
