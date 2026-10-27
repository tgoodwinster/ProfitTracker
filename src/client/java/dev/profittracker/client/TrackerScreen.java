package dev.profittracker.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Uses vanilla widgets for normal mouse, keyboard, and narrator support. */
final class TrackerScreen extends Screen {
    private final ProfitTrackerClient mod;
    private int tab;
    private int left;
    private int top;
    private int panelWidth;
    private EditBox targetInput;
    private String error = "";

    TrackerScreen(ProfitTrackerClient mod, int tab) {
        super(Component.literal("TgoodUtils"));
        this.mod = mod;
        this.tab = tab;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(360, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(5, (height - 230) / 2);
        int gap = 4;
        int tabWidth = (panelWidth - gap * 2) / 3;
        String[] names = {"Coal", "Endermites", "HUD"};
        for (int i = 0; i < names.length; i++) {
            final int selected = i;
            var button = button(names[i], left + i * (tabWidth + gap), top + 23, tabWidth, () -> {
                if (!applyTarget()) return;
                tab = selected;
                targetInput = null;
                rebuildWidgets();
            });
            button.active = tab != i;
        }
        switch (tab) {
            case 0 -> coalTab();
            case 1 -> endermiteTab();
            default -> hudTab();
        }
        button("Done", left, top + 202, panelWidth, this::onClose);
    }

    private void coalTab() {
        button("Coal HUD: " + onOff(mod.settings().coalHudEnabled), left, top + 64, panelWidth, () -> {
            mod.settings().coalHudEnabled = !mod.settings().coalHudEnabled;
            saveAndRefresh();
        });
        button(mod.coalRunning() ? "Pause session" : "Start / resume session", left, top + 90, panelWidth, () -> {
            mod.toggleCoal();
            rebuildWidgets();
        });
        button("Reset coal session", left, top + 116, panelWidth, mod::resetCoal);
    }

    private void endermiteTab() {
        button("Endermite tracker: " + onOff(mod.settings().endermitesEnabled), left, top + 54, panelWidth, () -> {
            if (!applyTarget()) return;
            mod.settings().endermitesEnabled = !mod.settings().endermitesEnabled;
            saveAndRefresh();
        });
        button("Target title: " + onOff(mod.settings().endermiteTitle), left, top + 80, panelWidth, () -> {
            if (!applyTarget()) return;
            mod.settings().endermiteTitle = !mod.settings().endermiteTitle;
            saveAndRefresh();
        });
        targetInput = new EditBox(font, left + panelWidth - 65, top + 109, 65, 20, Component.literal("Endermite alert threshold"));
        targetInput.setMaxLength(3);
        targetInput.setValue(Integer.toString(mod.settings().endermiteTarget));
        addRenderableWidget(targetInput);
        button("Apply threshold", left, top + 136, panelWidth, this::applyTarget);
    }

    private void hudTab() {
        button("Drag HUD positions", left, top + 64, panelWidth,
                () -> minecraft.gui.setScreen(new HudEditorScreen(mod, this)));
        button("Reset HUD positions", left, top + 90, panelWidth, () -> {
            mod.settings().resetPositions();
            saveAndRefresh();
        });
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private boolean applyTarget() {
        if (targetInput == null) return true;
        try {
            int number = Integer.parseInt(targetInput.getValue().trim());
            if (number < 0 || number > 200) throw new NumberFormatException();
            mod.settings().endermiteTarget = number;
            error = mod.saveSettings() ? "" : "Could not save settings. See the game log.";
            return true;
        } catch (NumberFormatException ex) {
            error = "Enter a whole number from 0 to 200.";
            return false;
        }
    }

    private void saveAndRefresh() {
        error = mod.saveSettings() ? "" : "Could not save settings. See the game log.";
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (!applyTarget()) return;
        if (!mod.saveSettings()) ProfitTrackerClient.feedback("Settings could not be saved. See the game log.");
        minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top + 6, 0xFFFFD166);
        if (tab == 0) {
            line(graphics, "Coal profit tracking in the Crystal Hollows.", 148);
            line(graphics, "HUD visibility does not pause your session.", 162);
        } else if (tab == 1) {
            graphics.text(font, "Alert threshold (0 = off)", left + 3, top + 115, 0xFFF3F6FA);
            line(graphics, "Living Endermites within 48 blocks in The End.", 163);
            line(graphics, "Includes other players' mobs. Set your own cap.", 177);
        } else {
            line(graphics, "Or use /tu hud to move both counters.", 127);
            line(graphics, "Drag a panel. Escape or Done saves it.", 142);
        }
        if (!error.isEmpty()) graphics.centeredText(font, error, width / 2, top + 190, 0xFFFF7777);
    }

    private void line(GuiGraphicsExtractor graphics, String text, int y) {
        graphics.text(font, text, left + 3, top + y, 0xFFB7C5D3);
    }

    private static String onOff(boolean enabled) { return enabled ? "ON" : "OFF"; }
}
