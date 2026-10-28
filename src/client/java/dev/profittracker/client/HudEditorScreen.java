package dev.profittracker.client;

import dev.profittracker.TrackerSettings.HudPosition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class HudEditorScreen extends Screen {
    private final ProfitTrackerClient mod;
    private final Screen parent;
    private HudPosition dragging;
    private int draggedWidth;
    private int draggedHeight;
    private double offsetX;
    private double offsetY;

    HudEditorScreen(ProfitTrackerClient mod, Screen parent) {
        super(Component.literal("Move HUDs"));
        this.mod = mod;
        this.parent = parent;
    }

    @Override
    protected void init() {
        dragging = null;
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
        mod.drawPanel(graphics, mod.coalLines(), mod.settings().coalPosition, false, true);
        mod.drawPanel(graphics, mod.endermiteLines(), mod.settings().endermitePosition, mod.endermitesAtTarget(), true);
        mod.drawPanel(graphics, mod.gemLines(), mod.settings().gemPosition, false, true);
        graphics.centeredText(font, "Drag a HUD. Escape or Done saves.", width / 2, height - 64, 0xFFFFFFFF);
        graphics.centeredText(font, "All three trackers are shown as previews.", width / 2, height - 53, 0xFFB7C5D3);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        // Endermites are drawn last, so prefer that panel if the previews overlap.
        return beginDrag(event, mod.settings().gemPosition, mod.gemLines())
                || beginDrag(event, mod.settings().endermitePosition, mod.endermiteLines())
                || beginDrag(event, mod.settings().coalPosition, mod.coalLines());
    }

    private boolean beginDrag(MouseButtonEvent event, HudPosition position, java.util.List<String> lines) {
        int w = mod.panelWidth(lines, width);
        int h = mod.panelHeight(lines);
        int x = position.left(width, w);
        int y = position.top(height, h);
        if (event.x() < x || event.x() >= x + w || event.y() < y || event.y() >= y + h) return false;
        dragging = position;
        draggedWidth = w;
        draggedHeight = h;
        offsetX = event.x() - x;
        offsetY = event.y() - y;
        setDragging(true);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null || event.button() != 0) return super.mouseDragged(event, dx, dy);
        dragging.moveTo(event.x() - offsetX, event.y() - offsetY, width, height, draggedWidth, draggedHeight);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.button() == 0) {
            dragging = null;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (!mod.saveSettings()) ProfitTrackerClient.feedback("HUD positions could not be saved. See the game log.");
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
