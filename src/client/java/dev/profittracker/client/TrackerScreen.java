package dev.profittracker.client;

import dev.profittracker.ChatTitleRule;
import dev.profittracker.CompactNumbers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;

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
    private int frameX, frameY, frameWidth, frameHeight, bodyTop, bodyBottom, scroll, maxScroll;
    private record Control(AbstractWidget widget, int y) { }
    private record Note(String text, int y) { }
    private final List<Control> controls = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private static final String[] NAMES = {"Coal", "Endermites", "Titles", "HUD", "Books", "Gems", "Visitors"};
    private static final String[] HINTS = {"Observed coal profit", "Live count, kill rate and Bestiary total", "Your chat-triggered alerts", "Move your displays", "Book combining and keybinds", "Gemstone profit and average rate", "Offline visitor estimates and alerts"};

    TrackerScreen(ProfitTrackerClient mod, int tab) {
        super(Component.literal("TgoodUtils"));
        this.mod = mod;
        this.tab = tab;
    }

    @Override
    protected void init() {
        minDelaySlider = maxDelaySlider = null;
        controls.clear(); notes.clear();
        tab = Math.clamp(tab, 0, NAMES.length - 1);
        mod.rememberTab(tab);
        frameWidth = Math.min(640, width - 20); frameHeight = Math.min(430, height - 20);
        frameX = (width - frameWidth) / 2; frameY = (height - frameHeight) / 2;
        left = frameX + 112; panelWidth = frameWidth - 126;
        bodyTop = frameY + 49; bodyBottom = frameY + frameHeight - 22; top = bodyTop - 54;
        for (int i = 0; i < NAMES.length; i++) {
            final int selected = i;
            var nav = new SettingsButton(frameX + 8, frameY + 45 + i * 21, 94, NAMES[i], ignored -> {
                if (!saveSettings()) return;
                tab = selected; scroll = 0; rebuildWidgets();
            }, tab == i);
            nav.active = tab != i; addRenderableWidget(nav);
        }
        addRenderableWidget(new SettingsButton(frameX + 8, frameY + frameHeight - 27, 94, "Done", ignored -> onClose(), false));
        int first = children().size();
        switch (tab) {
            case 0 -> coalTab();
            case 1 -> endermiteTab();
            case 2 -> customTitlesTab();
            case 4 -> booksTab();
            case 5 -> gemsTab();
            case 6 -> visitorsTab();
            default -> hudTab();
        }
        for (int i = first; i < children().size(); i++) if (children().get(i) instanceof AbstractWidget widget) controls.add(new Control(widget, widget.getY()));
        int contentBottom = controls.stream().mapToInt(c -> c.y + c.widget.getHeight()).max().orElse(bodyTop);
        for (var note : notes) contentBottom = Math.max(contentBottom, note.y + 12);
        maxScroll = Math.max(0, contentBottom - bodyBottom + 4);
        layout();
    }
    private void layout() {
        scroll = Math.clamp(scroll, 0, maxScroll);
        for (var control : controls) {
            control.widget.setY(control.y - scroll);
            control.widget.visible = control.widget.getY() >= bodyTop && control.widget.getY() + control.widget.getHeight() <= bodyBottom;
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x < left + panelWidth + 8 && y >= bodyTop && y <= bodyBottom) {
            scroll -= (int) Math.round(vertical * 26); layout(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        boolean result = super.mouseReleased(event);
        // Rebuild linked count bounds and start/resume state after a visitor slider gesture.
        if (tab == 6 && minecraft.gui.screen() == this) rebuildWidgets();
        return result;
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
        button("Tracker: " + onOff(mod.settings().endermitesEnabled), left, top + 54, panelWidth / 2 - 3, () -> {
            mod.settings().endermitesEnabled = !mod.settings().endermitesEnabled; saveAndRefresh();
        });
        button("Cap alert: " + onOff(mod.settings().endermiteTitle), left + panelWidth / 2 + 3, top + 54, panelWidth / 2 - 3, () -> {
            mod.settings().endermiteTitle = !mod.settings().endermiteTitle; saveAndRefresh();
        });
        addRenderableWidget(new SettingSlider(left, top + 80, panelWidth, 0, 200, 1, mod.settings().endermiteTarget,
                n -> n == 0 ? "Cap alert: disabled" : "Cap: " + (int) n, n -> mod.settings().endermiteTarget = (int) n));
        addRenderableWidget(new SettingSlider(left, top + 106, panelWidth, 4, 128, 1, mod.settings().endermiteRange,
                n -> "Range: " + (int) n + " blocks", n -> mod.settings().endermiteRange = (int) n));
        button("Kill source: " + (mod.settings().endermiteNearbyKills ? "Nearby deaths" : "Your hits"), left, top + 132, panelWidth, () -> {
            mod.settings().endermiteNearbyKills = !mod.settings().endermiteNearbyKills; mod.resetEndermiteSession(); saveAndRefresh();
        }).setTooltip(Tooltip.create(Component.literal("Your hits counts confirmed deaths after your recent hit. Nearby deaths includes other players' kills. Neither counts mobs unloading.")));
        button("Reset session", left, top + 158, panelWidth / 2 - 3, mod::resetEndermiteSession);
        button("Sync Bestiary", left + panelWidth / 2 + 3, top + 158, panelWidth / 2 - 3, mod::syncBestiaryNow);
        button(mod.bestiary().hasKey() ? "API key settings" : "Add Hypixel API key", left, top + 184, panelWidth, () -> {
            if (saveSettings()) minecraft.gui.setScreen(new ApiKeyScreen(mod, this));
        }).setTooltip(Tooltip.create(Component.literal(mod.bestiary().status())));
        button("Title and sound", left, top + 210, panelWidth / 2 - 3, () -> {
            if (!saveSettings()) return;
            minecraft.gui.setScreen(new AlertEditorScreen(mod, this, mod.settings().endermiteAlert, style -> { mod.settings().endermiteAlert = style; mod.saveSettings(); }));
        });
        button("Test cap alert", left + panelWidth / 2 + 3, top + 210, panelWidth / 2 - 3, () -> mod.previewAlert(mod.settings().endermiteAlert));
        notes.add(new Note(mod.bestiary().status(), top + 240));
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
        button("Key: " + mod.books().keyName() + " (Controls)", left, top + 178, panelWidth / 2 - 4, () -> {
            if (saveSettings()) mod.books().openKeybinds(this);
        });
        button("Clear profile", left + panelWidth / 2 + 4, top + 178, panelWidth / 2 - 4, () -> {
            if (!saveSettings()) return;
            var replacement = new dev.profittracker.BookCombinerSettings();
            replacement.minDelayMs = profile.minDelayMs;
            replacement.maxDelayMs = profile.maxDelayMs;
            replacement.maxLevel = profile.maxLevel;
            mod.settings().books = replacement;
            saveAndRefresh();
        });
    }

    private void gemsTab() {
        button("HUD: " + onOff(mod.settings().gems.hudEnabled), left, top + 56, panelWidth / 2 - 4, () -> {
            mod.settings().gems.hudEnabled = !mod.settings().gems.hudEnabled;
            saveAndRefresh();
        });
        button(mod.gemstones().running() ? "Pause gemstone session" : "Start / resume gemstone session", left, top + 82, panelWidth, () -> {
            mod.toggleGems();
            rebuildWidgets();
        });
        button("Timer: " + (mod.settings().gems.miningTimeOnly ? "Mining activity" : "Running session"), left, top + 108, panelWidth, () -> {
            mod.settings().gems.miningTimeOnly = !mod.settings().gems.miningTimeOnly;
            mod.gemstones().interrupt(ProfitTrackerClient.now());
            saveAndRefresh();
        });
        button("Glossy: " + onOff(mod.settings().gems.includeGlossy), left + panelWidth / 2 + 4, top + 56, panelWidth / 2 - 4, () -> {
            mod.settings().gems.includeGlossy = !mod.settings().gems.includeGlossy;
            saveAndRefresh();
        });
        var average = new SettingSlider(left, top + 134, panelWidth, 1, 10, 1, mod.settings().gems.averageMinutes,
                n -> "Average over " + (int) n + " min", n -> mod.settings().gems.averageMinutes = (int) n);
        average.setTooltip(Tooltip.create(Component.literal("Rolling running-session average. Warms up for a full window after starting or resetting.")));
        addRenderableWidget(average);
        button("Glossy notification format", left, top + 160, panelWidth, () -> {
            if (saveSettings()) minecraft.gui.setScreen(new GlossyFormatScreen(mod, this));
        });
    }

    private void visitorsTab() {
        var v = mod.settings().visitors;
        button("Timer: " + onOff(v.enabled), left, top + 54, panelWidth / 2 - 3, () -> { mod.toggleVisitors(); saveAndRefresh(); });
        button("HUD: " + onOff(v.hudEnabled), left + panelWidth / 2 + 3, top + 54, panelWidth / 2 - 3, () -> { v.hudEnabled = !v.hudEnabled; saveAndRefresh(); });
        addRenderableWidget(new SettingSlider(left, top + 80, panelWidth, 1, 30, 1, v.capacity, n -> "Capacity: " + (int)n,
                n -> mod.reconfigureVisitors((int)n, v.intervalSeconds)));
        addRenderableWidget(new SettingSlider(left, top + 106, panelWidth, 0, 7200, 15, v.intervalSeconds,
                n -> n == 0 ? "Time per visitor: unset" : "Time per visitor: " + CompactNumbers.duration((long)n * 1000),
                n -> mod.reconfigureVisitors(v.capacity, (int)n)));
        addRenderableWidget(new SettingSlider(left, top + 132, panelWidth, 0, v.capacity, 1, mod.visitors().count(System.currentTimeMillis()),
                n -> "Current visitors: " + (int)n, n -> mod.correctVisitors((int)n)));
        button(v.running ? "Pause timer" : "Start / resume", left, top + 158, panelWidth / 2 - 3, () -> { mod.toggleVisitorClock(); saveAndRefresh(); }).active = v.intervalSeconds > 0 && v.enabled;
        button("Visitors cleared", left + panelWidth / 2 + 3, top + 158, panelWidth / 2 - 3, () -> { mod.correctVisitors(0); saveAndRefresh(); });
        button("Auto outside Garden: " + onOff(v.autoGarden), left, top + 184, panelWidth, () -> { v.autoGarden = !v.autoGarden; saveAndRefresh(); });
        button("Full alert: " + onOff(v.alertEnabled), left, top + 210, panelWidth, () -> { v.alertEnabled = !v.alertEnabled; saveAndRefresh(); });
        button("Title and sound", left, top + 236, panelWidth / 2 - 3, () -> {
            if (!saveSettings()) return;
            minecraft.gui.setScreen(new AlertEditorScreen(mod, this, v.alert, style -> { v.alert = style; mod.saveSettings(); }, true));
        });
        button("Test 5 pings", left + panelWidth / 2 + 3, top + 236, panelWidth / 2 - 3, mod::previewVisitors);
        notes.add(new Note("Estimate only. Set your interval, then start.", top + 267));
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new SettingsButton(x, y, w, label, ignored -> action.run(), false));
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

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent key) {
        if (key.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN || key.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scroll += (key.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(26, bodyBottom - bodyTop - 24);
            layout(); return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, 0xF0151E2A);
        graphics.fill(frameX, frameY, frameX + 106, frameY + frameHeight, 0xF0101721);
        graphics.fill(frameX, frameY, frameX + frameWidth, frameY + 2, 0xFF75C8FF);
        graphics.text(font, "TgoodUtils", frameX + 10, frameY + 13, 0xFFF2F6FC);
        graphics.text(font, "Utilities", frameX + 10, frameY + 28, 0xFF7E91A5);
        graphics.text(font, NAMES[tab], left, frameY + 12, 0xFF75C8FF);
        graphics.text(font, font.plainSubstrByWidth(HINTS[tab], panelWidth), left, frameY + 28, 0xFF93A6BA);
        graphics.fill(left, frameY + 42, left + panelWidth, frameY + 43, 0xFF2A394B);
        graphics.enableScissor(left, bodyTop, left + panelWidth, bodyBottom);
        for (var note : notes) graphics.text(font, font.plainSubstrByWidth(note.text, panelWidth - 6), left + 3, note.y - scroll, 0xFF93A6BA);
        graphics.disableScissor();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (maxScroll > 0) {
            int track = bodyBottom - bodyTop;
            int thumb = Math.max(16, track * track / (track + maxScroll));
            int y = bodyTop + (track - thumb) * scroll / maxScroll;
            graphics.fill(left + panelWidth + 4, bodyTop, left + panelWidth + 6, bodyBottom, 0xFF273546);
            graphics.fill(left + panelWidth + 4, y, left + panelWidth + 6, y + thumb, 0xFF75C8FF);
            if (error.isEmpty()) graphics.text(font, "Scroll for more", left, frameY + frameHeight - 14, 0xFF70869D);
        }
        if (!error.isEmpty()) graphics.text(font, font.plainSubstrByWidth(error, panelWidth), left, frameY + frameHeight - 14, 0xFFFF8585);
    }

    private static String onOff(boolean enabled) { return enabled ? "ON" : "OFF"; }
}
