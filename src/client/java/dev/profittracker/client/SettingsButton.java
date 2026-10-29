package dev.profittracker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat vanilla button: standard input/narration, without the stretched stone texture. */
final class SettingsButton extends Button {
    private final boolean selected;
    SettingsButton(int x, int y, int width, String text, OnPress action, boolean selected) {
        super(x, y, width, 20, Component.literal(text), action, DEFAULT_NARRATION); this.selected = selected;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int background = selected ? 0xFF224360 : isHoveredOrFocused() && active ? 0xFF2D3E51 : 0xFF202B38;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        if (selected || isHoveredOrFocused() && active) g.fill(getX(), getY(), getX() + 2, getY() + getHeight(), 0xFF75C8FF);
        var font = Minecraft.getInstance().font;
        String text = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 12);
        g.centeredText(font, text, getX() + getWidth() / 2, getY() + 6, active || selected ? 0xFFF0F5FC : 0xFF738292);
    }
}
