package dev.profittracker.client;

import dev.profittracker.ChatTitleRule;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
    private SettingSlider minDelaySlider;
    private SettingSlider maxDelaySlider;
    private int titlesPage;
    private String error = "";

    TrackerScreen(ProfitTrackerClient mod, int tab) {
        super(Component.literal("TgoodUtils"));
        this.mod = mod;
        this.tab = tab;
    }

    @Override
    protected void init() {
        minDelaySlider = maxDelaySlider = null;
        panelWidth = Math.min(420, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(5, (height - 230) / 2);
        int gap = 4;
        int tabWidth = (panelWidth - gap * 4) / 5;
        String[] names = {"Coal", "Endermites", "Titles", "HUD", "Books"};
        for (int i = 0; i < names.length; i++) {
            final int selected = i;
            var button = button(names[i], left + i * (tabWidth + gap), top + 23, tabWidth, () -> {
                if (!saveSettings()) return;
                tab = selected;
                rebuildWidgets();
            });
            button.active = tab != i;
        }
        switch (tab) {
            case 0 -> coalTab();
            case 1 -> endermiteTab();
            case 2 -> customTitlesTab();
            case 4 -> booksTab();
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
            if (!saveSettings()) return;
            mod.settings().endermitesEnabled = !mod.settings().endermitesEnabled;
            saveAndRefresh();
        });
        button("Title: " + onOff(mod.settings().endermiteTitle), left + panelWidth / 2 + 4, top + 54, panelWidth / 2 - 4, () -> {
            if (!saveSettings()) return;
            mod.settings().endermiteTitle = !mod.settings().endermiteTitle;
            saveAndRefresh();
        });
        addRenderableWidget(new SettingSlider(left, top + 82, panelWidth, 0, 200, 1, mod.settings().endermiteTarget,
                n -> n == 0 ? "Alert threshold: OFF" : "Alert threshold: " + (int) n,
                n -> mod.settings().endermiteTarget = (int) n));
        addRenderableWidget(new SettingSlider(left, top + 110, panelWidth, 4, 128, 1, mod.settings().endermiteRange,
                n -> "Range: " + (int) n + " blocks", n -> mod.settings().endermiteRange = (int) n));
        button("Configure title and sound", left, top + 140, panelWidth, () -> {
            if (!saveSettings()) return;
            minecraft.gui.setScreen(new AlertEditorScreen(mod, this, mod.settings().endermiteAlert, style -> {
                mod.settings().endermiteAlert = style;
                mod.saveSettings();
            }));
        });
        button("Test alert", left, top + 166, panelWidth, () -> {
            if (saveSettings()) mod.previewAlert(mod.settings().endermiteAlert);
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

    private void booksTab() {
        var profile = mod.settings().books;
        minDelaySlider = addRenderableWidget(new SettingSlider(left, top + 78, panelWidth, 500, 5_000, 50, profile.minDelayMs,
                n -> "Minimum delay: " + (int) n + " ms", n -> {
                    profile.minDelayMs = (int) n;
                    if (profile.maxDelayMs < profile.minDelayMs) {
                        profile.maxDelayMs = profile.minDelayMs;
                        maxDelaySlider.setNumber(n);
                    }
                }));
        maxDelaySlider = addRenderableWidget(new SettingSlider(left, top + 102, panelWidth, 500, 5_000, 50, profile.maxDelayMs,
                n -> "Maximum delay: " + (int) n + " ms", n -> {
                    profile.maxDelayMs = (int) n;
                    if (profile.minDelayMs > profile.maxDelayMs) {
                        profile.minDelayMs = profile.maxDelayMs;
                        minDelaySlider.setNumber(n);
                    }
                }));
        int minTier = profile.configured() ? profile.inputLevel + 1 : 2;
        addRenderableWidget(new SettingSlider(left, top + 126, panelWidth, minTier, 10, 1, profile.maxLevel,
                n -> "Maximum output tier: " + (int) n, n -> profile.maxLevel = (int) n));
        button("Learn menu slots and book", left, top + 154, panelWidth, () -> {
            if (saveSettings()) mod.books().learn();
        });
        button("Clear learned profile", left, top + 178, panelWidth, () -> {
            if (!saveSettings()) return;
            var replacement = new dev.profittracker.BookCombinerSettings();
            replacement.minDelayMs = profile.minDelayMs;
            replacement.maxDelayMs = profile.maxDelayMs;
            replacement.maxLevel = profile.maxLevel;
            mod.settings().books = replacement;
            saveAndRefresh();
        });
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private boolean saveSettings() {
        error = mod.saveSettings() ? "" : "Could not save settings. See the game log.";
        return error.isEmpty();
    }

    private void saveAndRefresh() {
        error = mod.saveSettings() ? "" : "Could not save settings. See the game log.";
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (!saveSettings()) ProfitTrackerClient.feedback("Settings could not be saved. See the game log.");
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
        } else if (tab == 2) {
            if (mod.settings().customTitles.isEmpty()) line(graphics, "Add a title to match a chat message.", 116);
            line(graphics, "Page " + (titlesPage + 1) + " / " + Math.max(1, (mod.settings().customTitles.size() + 2) / 3), 192);
        } else if (tab == 4) {
            var profile = mod.settings().books;
            line(graphics, profile.configured() ? font.plainSubstrByWidth("Profile: " + profile.menuTitle + " | " + profile.enchantment, panelWidth - 6)
                    : "Learn the empty menu, then select a book.", 50);
            if (error.isEmpty()) line(graphics, "Random delay per click | " + mod.books().keyName() + " starts / stops.", 64);
        } else if (tab == 3) {
            line(graphics, "Or use /tu hud to move both counters.", 127);
            line(graphics, "Drag a panel. Escape or Done saves it.", 142);
        }
        if (!error.isEmpty()) graphics.centeredText(font, font.plainSubstrByWidth(error, panelWidth), width / 2,
                top + (tab == 4 ? 64 : 190), 0xFFFF7777);
    }

    private void line(GuiGraphicsExtractor graphics, String text, int y) {
        graphics.text(font, text, left + 3, top + y, 0xFFB7C5D3);
    }

    private static String onOff(boolean enabled) { return enabled ? "ON" : "OFF"; }
}
