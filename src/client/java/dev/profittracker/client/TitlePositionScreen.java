package dev.profittracker.client;

import dev.profittracker.AlertStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class TitlePositionScreen extends Screen {
    private final Screen parent;
    private final AlertStyle style;
    private boolean moving;
    private double offsetX;
    private double offsetY;

    TitlePositionScreen(Screen parent, AlertStyle style) {
        super(Component.literal("Move title"));
        this.parent = parent;
        this.style = style;
    }

    @Override
    protected void init() {
        moving = false;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 50, height - 27, 100, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        var box = AlertRenderer.bounds(style, width, height);
        graphics.fill(box.x() - 3, box.y() - 3, box.x() + box.width() + 3, box.y() + box.height() + 3, 0x80000000);
        AlertRenderer.draw(graphics, style);
        graphics.centeredText(font, "Drag the title. Escape or Done returns to alert settings.", width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        var box = AlertRenderer.bounds(style, width, height);
        if (event.button() != 0 || event.x() < box.x() || event.x() > box.x() + box.width()
                || event.y() < box.y() || event.y() > box.y() + box.height()) return false;
        moving = true;
        offsetX = event.x() - box.x();
        offsetY = event.y() - box.y();
        setDragging(true);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!moving || event.button() != 0) return super.mouseDragged(event, dx, dy);
        var box = AlertRenderer.bounds(style, width, height);
        style.position.moveTo(event.x() - offsetX, event.y() - offsetY, width, height, box.width(), box.height());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!moving) return super.mouseReleased(event);
        moving = false;
        setDragging(false);
        return true;
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
