package dev.profittracker.client;

import dev.profittracker.AlertStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One shared editor so Endermite and chat alerts have exactly the same options. */
final class AlertEditorScreen extends Screen {
    private final ProfitTrackerClient mod;
    private final Screen parent;
    private final Consumer<AlertStyle> save;
    private final AlertStyle draft;
    private final Map<String, EditBox> fields = new HashMap<>();
    private int page;
    private int left;
    private int top;
    private int panel;
    private String error = "";

    AlertEditorScreen(ProfitTrackerClient mod, Screen parent, AlertStyle style, Consumer<AlertStyle> save) {
        super(Component.literal("Alert settings"));
        this.mod = mod;
        this.parent = parent;
        this.save = save;
        draft = style.copy();
    }

    @Override
    protected void init() {
        fields.clear();
        panel = Math.min(380, width - 16);
        left = (width - panel) / 2;
        top = Math.max(5, (height - 230) / 2);
        String[] tabs = {"Text", "Sound", "Position"};
        int w = (panel - 8) / 3;
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            button(tabs[i], left + i * (w + 4), top + 24, w, () -> {
                if (!applyFields()) return;
                page = target;
                rebuildWidgets();
            }).active = page != i;
        }
        if (page == 0) {
            field("text", draft.text, left, top + 62, panel, 96);
            field("color", "#" + draft.color, left, top + 105, panel / 2 - 4, 7);
            field("scale", Double.toString(draft.scale), left + panel / 2 + 4, top + 105, panel / 2 - 4, 5);
            button("Bold: " + (draft.bold ? "ON" : "OFF"), left, top + 144, panel / 2 - 4, () -> {
                if (applyFields()) { draft.bold = !draft.bold; rebuildWidgets(); }
            });
            field("seconds", Double.toString(draft.seconds), left + panel / 2 + 4, top + 144, panel / 2 - 4, 5);
        } else if (page == 1) {
            button("Sound: " + (draft.soundEnabled ? "ON" : "MUTED"), left, top + 60, panel, () -> {
                if (applyFields()) { draft.soundEnabled = !draft.soundEnabled; rebuildWidgets(); }
            });
            button("Noise: " + draft.sound.name().replace('_', ' '), left, top + 88, panel, () -> {
                if (!applyFields()) return;
                var values = AlertStyle.Sound.values();
                draft.sound = values[(draft.sound.ordinal() + 1) % values.length];
                rebuildWidgets();
            });
            field("volume", Integer.toString((int) Math.round(draft.volume * 100)), left + panel - 70, top + 122, 70, 3);
        } else {
            field("x", String.format(java.util.Locale.ROOT, "%.2f", draft.position.x * 100), left, top + 75, panel / 2 - 4, 6);
            field("y", String.format(java.util.Locale.ROOT, "%.2f", draft.position.y * 100), left + panel / 2 + 4, top + 75, panel / 2 - 4, 6);
            button("Drag title position", left, top + 110, panel, () -> {
                if (applyFields()) {
                    mod.clearAlert();
                    minecraft.gui.setScreen(new TitlePositionScreen(this, draft));
                }
            });
            button("Center title", left, top + 138, panel, () -> {
                if (applyFields()) { draft.position.x = 0.5; draft.position.y = 0.5; rebuildWidgets(); }
            });
        }
        button("Test alert", left, top + 170, panel, () -> { if (applyFields()) mod.previewAlert(draft); });
        button("Save", left, top + 202, panel / 2 - 4, () -> {
            if (!applyFields()) return;
            save.accept(draft.copy());
            minecraft.gui.setScreen(parent);
        });
        button("Cancel", left + panel / 2 + 4, top + 202, panel / 2 - 4, this::onClose);
    }

    private void field(String key, String value, int x, int y, int w, int max) {
        var field = new EditBox(font, x, y, w, 20, Component.literal(key));
        field.setMaxLength(max);
        field.setValue(value);
        fields.put(key, addRenderableWidget(field));
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, w, 20).build());
    }

    private double number(String key, double min, double max) {
        double value = Double.parseDouble(fields.get(key).getValue().trim());
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException();
        return value;
    }

    private boolean applyFields() {
        try {
            if (page == 0) {
                String text = fields.get("text").getValue().trim();
                String color = fields.get("color").getValue().trim().replace("#", "");
                if (text.isEmpty() || !color.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
                double scale = number("scale", 0.5, 8);
                double seconds = number("seconds", 0.5, 15);
                draft.text = text;
                draft.color = AlertStyle.normalizeColor(color);
                draft.scale = scale;
                draft.seconds = seconds;
            } else if (page == 1) draft.volume = number("volume", 0, 100) / 100;
            else {
                double x = number("x", 0, 100);
                double y = number("y", 0, 100);
                draft.position.x = x / 100;
                draft.position.y = y / 100;
            }
            draft.sanitize();
            error = "";
            return true;
        } catch (IllegalArgumentException ex) {
            error = page == 0 ? "Use text, a hex color, size 0.5-8, and 0.5-15 seconds." : "Enter a number from 0 to 100.";
            return false;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 6, 0xFFFFD166);
        if (page == 0) {
            label(graphics, "Title message", left, 50);
            label(graphics, "Color (#RRGGBB)", left, 93);
            label(graphics, "Text size (0.5-8)", left + panel / 2 + 4, 93);
            label(graphics, "Seconds (0.5-15)", left + panel / 2 + 4, 132);
        } else if (page == 1) {
            label(graphics, "Volume (0-100%)", left, 128);
            label(graphics, "Minecraft's Master volume still applies.", left, 151);
        } else {
            label(graphics, "Horizontal (%)", left, 61);
            label(graphics, "Vertical (%)", left + panel / 2 + 4, 61);
        }
        if (!error.isEmpty()) graphics.centeredText(font, font.plainSubstrByWidth(error, panel), width / 2, top + 192, 0xFFFF7777);
    }

    private void label(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(font, text, x, top + y, 0xFFB7C5D3);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
