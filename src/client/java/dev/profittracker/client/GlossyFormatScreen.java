package dev.profittracker.client;

import dev.profittracker.GemMessages;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A literal template supports other mods' notifications without user-supplied regex. */
final class GlossyFormatScreen extends Screen {
    private final ProfitTrackerClient mod;
    private final Screen parent;
    private String draft;
    private int left, top, panel;
    private String error = "";

    GlossyFormatScreen(ProfitTrackerClient mod, Screen parent) {
        super(Component.literal("Glossy sack notification"));
        this.mod = mod;
        this.parent = parent;
        draft = mod.settings().gems.glossyFormat;
    }

    @Override protected void init() {
        panel = Math.min(420, width - 16);
        left = (width - panel) / 2;
        top = Math.max(5, (height - 230) / 2);
        var field = addRenderableWidget(new EditBox(font, left, top + 49, panel, 20, Component.literal("Glossy notification template")));
        field.setMaxLength(512);
        field.setValue(draft);
        field.setResponder(value -> draft = value);
        button("Use pasted example", top + 77, () -> {
            draft = GemMessages.formatFromExample(draft);
            rebuildWidgets();
        });
        button("Pick recent message", top + 105, () -> minecraft.gui.setScreen(new RecentMessagesScreen(mod, this,
                message -> draft = GemMessages.formatFromExample(message))));
        button("Restore default format", top + 133, () -> {
            draft = GemMessages.DEFAULT_GLOSSY_FORMAT;
            rebuildWidgets();
        });
        addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> {
            if (!GemMessages.validGlossyFormat(draft)) {
                error = "Use Glossy Gemstone and exactly one {amount}.";
                return;
            }
            mod.settings().gems.glossyFormat = draft;
            if (!mod.saveSettings()) ProfitTrackerClient.feedback("Glossy format could not be saved. See the game log.");
            minecraft.gui.setScreen(parent);
        }).bounds(left, top + 202, panel / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(left + panel / 2 + 4, top + 202, panel / 2 - 4, 20).build());
    }

    private void button(String text, int y, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), ignored -> action.run()).bounds(left, y, panel, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        graphics.centeredText(font, title, width / 2, top + 7, 0xFFFFD166);
        graphics.text(font, "Paste the exact notification (Ctrl+V).", left, top + 33, 0xFFB7C5D3);
        graphics.text(font, "Use {amount} for the changing quantity.", left, top + 165, 0xFFB7C5D3);
        graphics.text(font, "Only matching notifications while mining count.", left, top + 179, 0xFFB7C5D3);
        if (!error.isEmpty()) graphics.centeredText(font, font.plainSubstrByWidth(error, panel), width / 2, top + 191, 0xFFFF7777);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
