package dev.profittracker.client;

import dev.profittracker.PrivateApiKeyStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

final class ApiKeyScreen extends Screen {
    private final ProfitTrackerClient mod;
    private final Screen parent;
    private EditBox field;
    private String error = "";
    private int left, top, panel;
    ApiKeyScreen(ProfitTrackerClient mod, Screen parent) { super(Component.literal("Hypixel API key")); this.mod = mod; this.parent = parent; }
    @Override protected void init() {
        panel = Math.min(390, width - 24); left = (width - panel) / 2; top = Math.max(8, (height - 180) / 2);
        field = new EditBox(font, left + 12, top + 62, panel - 24, 20, Component.literal("API key, hidden")) {
            @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage() { return Component.literal("API key, hidden"); }
        };
        field.setMaxLength(128);
        field.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        field.setHint(Component.literal(mod.bestiary().hasKey() ? "Key saved; paste to replace" : "Paste your development key"));
        addRenderableWidget(field); setInitialFocus(field);
        addRenderableWidget(new SettingsButton(left + 12, top + 100, panel - 24, "Save key", ignored -> {
            String value = field.getValue().trim();
            if (!PrivateApiKeyStore.valid(value)) { error = "Paste a valid key, or use Clear key."; return; }
            try { mod.bestiary().setKey(value); field.setValue(""); minecraft.gui.setScreen(parent); }
            catch (java.io.IOException ex) { error = "Could not save the private key file."; }
        }, false));
        addRenderableWidget(new SettingsButton(left + 12, top + 128, (panel - 32) / 2, "Clear key", ignored -> {
            try { mod.bestiary().setKey(""); field.setValue(""); minecraft.gui.setScreen(parent); }
            catch (java.io.IOException ex) { error = "Could not clear the private key file."; }
        }, false));
        addRenderableWidget(new SettingsButton(left + panel / 2 + 4, top + 128, (panel - 32) / 2, "Cancel", ignored -> onClose(), false));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        g.fill(left, top, left + panel, top + 174, 0xF018202C);
        g.text(font, title, left + 12, top + 12, 0xFF75C8FF);
        g.text(font, "Bestiary totals refresh every 5 minutes.", left + 12, top + 32, 0xFFBCC8D7);
        g.text(font, "Stored only in tgoodutils-private.json.", left + 12, top + 46, 0xFFBCC8D7);
        super.extractRenderState(g, x, y, delta);
        if (!error.isEmpty()) g.text(font, font.plainSubstrByWidth(error, panel - 24), left + 12, top + 157, 0xFFFF8585);
    }
    @Override public void onClose() { if (field != null) field.setValue(""); minecraft.gui.setScreen(parent); }
}
