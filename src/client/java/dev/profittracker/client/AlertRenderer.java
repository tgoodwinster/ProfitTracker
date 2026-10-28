package dev.profittracker.client;

import dev.profittracker.AlertLayout;
import dev.profittracker.AlertStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class AlertRenderer {
    private AlertRenderer() { }

    private static Component text(AlertStyle style) {
        return Component.literal(style.text).withStyle(value -> value.withBold(style.bold));
    }

    static AlertLayout bounds(AlertStyle style, int width, int height) {
        var font = Minecraft.getInstance().font;
        return AlertLayout.calculate(style, font.width(text(style)), font.lineHeight, width, height);
    }

    static void draw(GuiGraphicsExtractor graphics, AlertStyle style) {
        var bounds = bounds(style, graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.x(), bounds.y());
        graphics.pose().scale((float) bounds.scale());
        graphics.text(Minecraft.getInstance().font, text(style), 0, 0, style.argb(), true);
        graphics.pose().popMatrix();
    }
}
