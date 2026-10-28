package dev.profittracker.client;

import dev.profittracker.ChatTitleRule;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
    private EditBox rangeInput;
    private int titlesPage;
    private String error = "";

    TrackerScreen(ProfitTrackerClient mod, int tab) {
        super(Component.literal("TgoodUtils"));
        this.mod = mod;
        this.tab = tab;
    }

    @Override
    protected void init() {
        targetInput = rangeInput = null;
        panelWidth = Math.min(420, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(5, (height - 230) / 2);
        int gap = 4;
        int tabWidth = (panelWidth - gap * 3) / 4;
        String[] names = {"Coal", "Endermites", "Custom Titles", "HUD"};
        for (int i = 0; i < names.length; i++) {
            final int selected = i;
            var button = button(names[i], left + i * (tabWidth + gap), top + 23, tabWidth, () -> {
                if (!applyTarget()) return;
                tab = selected;
                targetInput = null;
                rangeInput = null;
                rebuildWidgets();
            });
            button.active = tab != i;
        }
        switch (tab) {
            case 0 -> coalTab();
            case 1 -> endermiteTab();
            case 2 -> customTitlesTab();
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
        button("Tracker: " + onOff(mod.settings().endermitesEnabled), left, top + 54, panelWidth / 2 - 4, () -> {
            if (!applyTarget()) return;
            mod.settings().endermitesEnabled = !mod.settings().endermitesEnabled;
            saveAndRefresh();
        });
        button("Title: " + onOff(mod.settings().endermiteTitle), left + panelWidth / 2 + 4, top + 54, panelWidth / 2 - 4, () -> {
            if (!applyTarget()) return;
            mod.settings().endermiteTitle = !mod.settings().endermiteTitle;
            saveAndRefresh();
        });
        targetInput = new EditBox(font, left + panelWidth - 65, top + 82, 65, 20, Component.literal("Endermite alert threshold"));
        targetInput.setMaxLength(3);
        targetInput.setValue(Integer.toString(mod.settings().endermiteTarget));
        addRenderableWidget(targetInput);
        rangeInput = new EditBox(font, left + panelWidth - 65, top + 110, 65, 20, Component.literal("Endermite range in blocks"));
        rangeInput.setMaxLength(3);
        rangeInput.setValue(Integer.toString(mod.settings().endermiteRange));
        addRenderableWidget(rangeInput);
        button("Configure title and sound", left, top + 140, panelWidth, () -> {
            if (!applyTarget()) return;
            minecraft.gui.setScreen(new AlertEditorScreen(mod, this, mod.settings().endermiteAlert, style -> {
                mod.settings().endermiteAlert = style;
                mod.saveSettings();
            }));
        });
        button("Test alert", left, top + 166, panelWidth, () -> {
            if (applyTarget()) mod.previewAlert(mod.settings().endermiteAlert);
        });
    }

    private void customTitlesTab() {
        var rules = mod.settings().customTitles;
        titlesPage = Math.clamp(titlesPage, 0, Math.max(0, (rules.size() - 1) / 3));
        button("Custom Titles: " + onOff(mod.settings().customTitlesEnabled), left, top + 54, panelWidth, () -> {
            mod.settings().customTitlesEnabled = !mod.settings().customTitlesEnabled;
            saveAndRefresh();
        });
        for (int row = 0; row < 3 && titlesPage * 3 + row < rules.size(); row++) {
            var rule = rules.get(titlesPage * 3 + row);
            int y = top + 82 + row * 28;
            var edit = button(font.plainSubstrByWidth(rule.alert.text + " | " + rule.target, panelWidth - 130), left, y, panelWidth - 118,
                    () -> minecraft.gui.setScreen(new ChatRuleScreen(mod, this, rule, false)));
            edit.setTooltip(Tooltip.create(Component.literal(rule.target)));
            button(onOff(rule.enabled), left + panelWidth - 114, y, 48, () -> {
                rule.enabled = !rule.enabled;
                saveAndRefresh();
            });
            button("Delete", left + panelWidth - 62, y, 62, () -> {
                rules.remove(rule);
                saveAndRefresh();
            });
        }
        button("Add Custom Title", left, top + 168, panelWidth / 2, () ->
                minecraft.gui.setScreen(new ChatRuleScreen(mod, this, new ChatTitleRule(), true))).active = rules.size() < 50;
        button("<", left + panelWidth - 76, top + 168, 34, () -> { titlesPage--; rebuildWidgets(); }).active = titlesPage > 0;
        button(">", left + panelWidth - 36, top + 168, 34, () -> { titlesPage++; rebuildWidgets(); }).active = (titlesPage + 1) * 3 < rules.size();
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
            int range = Integer.parseInt(rangeInput.getValue().trim());
            if (range < 4 || range > 128) throw new NumberFormatException();
            mod.settings().endermiteTarget = number;
            mod.settings().endermiteRange = range;
            error = mod.saveSettings() ? "" : "Could not save settings. See the game log.";
            return true;
        } catch (NumberFormatException ex) {
            error = "Use a cap of 0-200 and a range of 4-128 blocks.";
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
            graphics.text(font, "Alert threshold (0 = off)", left + 3, top + 88, 0xFFF3F6FA);
            graphics.text(font, "Range (4-128 blocks)", left + 3, top + 116, 0xFFF3F6FA);
        } else if (tab == 2) {
            if (mod.settings().customTitles.isEmpty()) line(graphics, "Add a title to match a chat message.", 116);
            line(graphics, "Page " + (titlesPage + 1) + " / " + Math.max(1, (mod.settings().customTitles.size() + 2) / 3), 192);
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
