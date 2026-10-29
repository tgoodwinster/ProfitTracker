package dev.profittracker.client;

import dev.profittracker.AlertStyle;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared draft editor: sliders update the preview, and only Save commits changes. */
final class AlertEditorScreen extends Screen {
    private final ProfitTrackerClient mod;
    private final Screen parent;
    private final Consumer<AlertStyle> save;
    private final AlertStyle draft;
    private int page;
    private int left;
    private int top;
    private int panel;
    private String error = "";
    private final boolean burst;

    AlertEditorScreen(ProfitTrackerClient mod, Screen parent, AlertStyle style, Consumer<AlertStyle> save) {
        this(mod, parent, style, save, false);
    }
    AlertEditorScreen(ProfitTrackerClient mod, Screen parent, AlertStyle style, Consumer<AlertStyle> save, boolean burst) {
        super(Component.literal("Alert settings"));
        this.mod = mod;
        this.parent = parent;
        this.save = save;
        this.burst = burst;
        draft = style.copy();
    }

    @Override
    protected void init() {
        panel = Math.min(380, width - 16);
        left = (width - panel) / 2;
        top = Math.max(5, (height - 230) / 2);
        String[] tabs = {"Text", "Color", "Sound", "Position"};
        int w = (panel - 12) / 4;
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            button(tabs[i], left + i * (w + 4), top + 24, w, () -> {
                if (!applyFields()) return;
                page = target;
                rebuildWidgets();
            }).active = page != i;
        }
        switch (page) {
            case 0 -> textPage();
            case 1 -> colorPage();
            case 2 -> soundPage();
            default -> positionPage();
        }
        button(burst ? "Test 5 pings" : "Test alert", left, top + 170, panel, () -> {
            if (applyFields()) { if (burst) mod.previewVisitors(draft); else mod.previewAlert(draft); }
        });
        button("Save", left, top + 202, panel / 2 - 4, () -> {
            if (!applyFields()) return;
            save.accept(draft.copy());
            minecraft.gui.setScreen(parent);
        });
        button("Cancel", left + panel / 2 + 4, top + 202, panel / 2 - 4, this::onClose);
    }

    private void textPage() {
        var message = addRenderableWidget(new EditBox(font, left, top + 62, panel, 20, Component.literal("Title message")));
        message.setMaxLength(96);
        message.setValue(draft.text);
        message.setResponder(text -> draft.text = text);
        slider(94, 0.5, 8, 0.1, draft.scale, n -> String.format(Locale.ROOT, "Text size: %.1fx", n), n -> draft.scale = n);
        if (!burst) slider(120, 0.5, 15, 0.1, draft.seconds, n -> String.format(Locale.ROOT, "Duration: %.1f seconds", n), n -> draft.seconds = n);
        button("Bold: " + (draft.bold ? "ON" : "OFF"), left, top + 146, panel, () -> {
            if (applyFields()) { draft.bold = !draft.bold; rebuildWidgets(); }
        });
    }

    private void colorPage() {
        int rgb = draft.argb();
        slider(58, 0, 255, 1, (rgb >> 16) & 255, n -> "Red: " + (int) n, n -> channel(16, (int) n));
        slider(86, 0, 255, 1, (rgb >> 8) & 255, n -> "Green: " + (int) n, n -> channel(8, (int) n));
        slider(114, 0, 255, 1, rgb & 255, n -> "Blue: " + (int) n, n -> channel(0, (int) n));
    }

    private void channel(int shift, int value) {
        int rgb = draft.argb() & 0xFFFFFF;
        rgb = (rgb & ~(255 << shift)) | (value << shift);
        draft.color = String.format(Locale.ROOT, "%06X", rgb);
    }

    private void soundPage() {
        button("Sound: " + (draft.soundEnabled ? "ON" : "MUTED"), left, top + 60, panel, () -> {
            draft.soundEnabled = !draft.soundEnabled;
            rebuildWidgets();
        });
        button("Noise: " + draft.sound.name().replace('_', ' '), left, top + 88, panel, () -> {
            var values = AlertStyle.Sound.values();
            draft.sound = values[(draft.sound.ordinal() + 1) % values.length];
            rebuildWidgets();
        });
        slider(116, 0, 100, 1, draft.volume * 100, n -> "Volume: " + (int) n + "%", n -> draft.volume = n / 100);
    }

    private void positionPage() {
        slider(62, 0, 100, 0.1, draft.position.x * 100,
                n -> String.format(Locale.ROOT, "Horizontal: %.1f%%", n), n -> draft.position.x = n / 100);
        slider(90, 0, 100, 0.1, draft.position.y * 100,
                n -> String.format(Locale.ROOT, "Vertical: %.1f%%", n), n -> draft.position.y = n / 100);
        button("Drag title position", left, top + 118, panel, () -> {
            mod.clearAlert();
            minecraft.gui.setScreen(new TitlePositionScreen(this, draft));
        });
        button("Center title", left, top + 146, panel, () -> {
            draft.position.x = draft.position.y = 0.5;
            rebuildWidgets();
        });
    }

    private void slider(int y, double min, double max, double step, double initial,
                        DoubleFunction<String> label, DoubleConsumer changed) {
        addRenderableWidget(new SettingSlider(left, top + y, panel, min, max, step, initial, label, changed));
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, w, 20).build());
    }

    private boolean applyFields() {
        if (draft.text.isBlank()) {
            error = "Enter a title message.";
            return false;
        }
        draft.sanitize();
        error = "";
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 6, 0xFFFFD166);
        if (page == 0) {
            label(graphics, "Title message", 50);
            if (burst) label(graphics, "Five flashes, one second apart.", 125);
        }
        else if (page == 1) {
            graphics.text(font, "Color: #" + draft.color, left, top + 149, draft.argb());
            graphics.fill(left + panel - 45, top + 142, left + panel, top + 164, draft.argb());
        } else if (page == 2) label(graphics, "Minecraft's Master volume still applies.", 149);
        if (!error.isEmpty()) graphics.centeredText(font, error, width / 2, top + 192, 0xFFFF7777);
    }

    private void label(GuiGraphicsExtractor graphics, String text, int y) {
        graphics.text(font, text, left, top + y, 0xFFB7C5D3);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
