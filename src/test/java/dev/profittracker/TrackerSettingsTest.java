package dev.profittracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TrackerSettingsTest {
    @TempDir Path folder;

    @Test void firstRunUsesRequested25TargetWithoutCreatingAFile() throws IOException {
        var file = folder.resolve("profittracker.json");
        var settings = new SettingsStore(file).load();
        assertTrue(settings.coalHudEnabled);
        assertTrue(settings.endermitesEnabled);
        assertEquals(25, settings.endermiteTarget);
        assertFalse(Files.exists(file));
    }

    @Test void settingsAndHudPositionsSurviveReloadAndUpdates() throws IOException {
        var store = new SettingsStore(folder.resolve("config/profittracker.json"));
        var settings = store.load();
        settings.coalHudEnabled = false;
        settings.endermiteTitle = false;
        settings.endermiteTarget = 12;
        settings.endermitePosition.moveTo(150, 90, 400, 300, 100, 30);
        store.save(settings);
        var restored = store.load();
        assertFalse(restored.coalHudEnabled);
        assertFalse(restored.endermiteTitle);
        assertEquals(12, restored.endermiteTarget);
        assertEquals(150, restored.endermitePosition.left(400, 100));
        assertEquals(90, restored.endermitePosition.top(300, 30));
        restored.endermitesEnabled = false;
        store.save(restored);
        assertFalse(store.load().endermitesEnabled);
        try (var files = Files.list(folder.resolve("config"))) {
            assertEquals(1, files.count());
        }
    }

    @Test void partialOldSettingsKeepNewDefaultsAndNullPositionsAreRepaired() throws IOException {
        var file = folder.resolve("profittracker.json");
        Files.writeString(file, "{\"coalHudEnabled\":false,\"coalPosition\":null,\"endermiteTarget\":999}");
        var settings = new SettingsStore(file).load();
        assertFalse(settings.coalHudEnabled);
        assertTrue(settings.endermitesEnabled);
        assertEquals(200, settings.endermiteTarget);
        assertNotNull(settings.coalPosition);
        assertNotNull(settings.endermitePosition);
    }

    @Test void malformedConfigurationIsReportedWithoutOverwritingIt() throws IOException {
        var file = folder.resolve("profittracker.json");
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> new SettingsStore(file).load());
        assertEquals("{broken", Files.readString(file));
    }

    @Test void bookProfilePersistsButRunningStateDoesNotExistInConfiguration() throws IOException {
        var store = new SettingsStore(folder.resolve("books.json"));
        var settings = new TrackerSettings();
        settings.books.server = "example.net";
        settings.books.menuTitle = "Anvil";
        settings.books.rows = 6;
        settings.books.firstSlot = 29;
        settings.books.secondSlot = 33;
        settings.books.outputSlot = 13;
        settings.books.confirmSlot = 22;
        settings.books.confirmItem = "minecraft:anvil";
        settings.books.enchantment = "skyblock:sharpness";
        settings.books.delayMs = 1_000;
        settings.books.firstIdle = "minecraft:barrier\nInput";
        store.save(settings);
        var restored = store.load().books;
        assertTrue(restored.configured());
        assertEquals(5, restored.maxLevel);
        assertEquals(1_000, restored.delayMs);
        assertEquals(settings.books.firstIdle, restored.firstIdle);
        assertFalse(Files.readString(folder.resolve("books.json")).contains("running"));
    }

    @Test void olderAndNullBookSettingsGainSafeDefaults() throws IOException {
        var file = folder.resolve("old-books.json");
        Files.writeString(file, "{\"books\":null}");
        var books = new SettingsStore(file).load().books;
        assertNotNull(books);
        assertEquals(750, books.delayMs);
        assertEquals(5, books.maxLevel);
        assertFalse(books.configured());
    }

    @Test void unchangedGuiSavesDoNotRewriteTheConfigFile() throws IOException {
        var file = folder.resolve("settings.json");
        var store = new SettingsStore(file);
        var settings = new TrackerSettings();
        store.save(settings);
        var marker = java.nio.file.attribute.FileTime.fromMillis(1_234_000);
        Files.setLastModifiedTime(file, marker);
        store.save(settings);
        assertEquals(marker, Files.getLastModifiedTime(file));
        settings.endermiteRange = 64;
        store.save(settings);
        assertEquals(64, store.load().endermiteRange);
        assertNotEquals(marker, Files.getLastModifiedTime(file));
    }

    @Test void draggingClampsAllEdgesAndSurvivesGuiScaling() {
        var position = new TrackerSettings.HudPosition(0, 0);
        position.moveTo(500, -10, 400, 300, 100, 40);
        assertEquals(300, position.left(400, 100));
        assertEquals(0, position.top(300, 40));
        position.moveTo(150, 130, 400, 300, 100, 40);
        assertEquals(350, position.left(800, 100));
        assertEquals(280, position.top(600, 40));
        assertEquals(0, position.left(80, 100));
        assertEquals(0, position.top(30, 40));
    }

    @Test void invalidPositionNumbersCannotSendHudOffScreen() {
        var position = new TrackerSettings.HudPosition(Double.NaN, Double.POSITIVE_INFINITY);
        position.sanitize();
        assertEquals(0, position.left(400, 100));
        assertEquals(0, position.top(300, 40));
        position.moveTo(-100, 1000, 400, 300, 100, 40);
        assertEquals(0, position.left(400, 100));
        assertEquals(260, position.top(300, 40));
    }

    @Test void resetPositionsPreservesFeaturePreferences() {
        var settings = new TrackerSettings();
        settings.endermiteTarget = 20;
        settings.coalPosition.x = 1;
        settings.resetPositions();
        assertEquals(0.015, settings.coalPosition.x);
        assertEquals(20, settings.endermiteTarget);
    }

    @Test void oldConfigurationGainsNewAlertOptionsWithoutLosingMutedTitles() throws IOException {
        var file = folder.resolve("old.json");
        Files.writeString(file, "{\"endermiteTitle\":false,\"endermiteTarget\":25}");
        var settings = new SettingsStore(file).load();
        assertFalse(settings.endermiteTitle);
        assertEquals(48, settings.endermiteRange);
        assertEquals("KILL", settings.endermiteAlert.text);
        assertTrue(settings.endermiteAlert.soundEnabled);
        assertTrue(settings.customTitles.isEmpty());
    }

    @Test void customRulesAndAllAlertChoicesRoundTrip() throws IOException {
        var store = new SettingsStore(folder.resolve("alerts.json"));
        var settings = new TrackerSettings();
        var rule = new ChatTitleRule();
        rule.target = "Your ability is now available!";
        rule.exact = true;
        rule.ignoreCase = false;
        rule.alert.text = "READY";
        rule.alert.color = "00AAFF";
        rule.alert.scale = 6;
        rule.alert.seconds = 5;
        rule.alert.sound = AlertStyle.Sound.BELL;
        rule.alert.soundEnabled = false;
        rule.alert.position.x = 0.25;
        settings.endermiteRange = 80;
        settings.customTitles.add(rule);
        store.save(settings);
        var saved = store.load();
        var restored = saved.customTitles.getFirst();
        assertEquals(80, saved.endermiteRange);
        assertEquals(rule.id, restored.id);
        assertTrue(restored.exact);
        assertFalse(restored.ignoreCase);
        assertEquals("READY", restored.alert.text);
        assertEquals("00AAFF", restored.alert.color);
        assertEquals(6, restored.alert.scale);
        assertEquals(5, restored.alert.seconds);
        assertEquals(AlertStyle.Sound.BELL, restored.alert.sound);
        assertFalse(restored.alert.soundEnabled);
        assertEquals(0.25, restored.alert.position.x);
    }

    @Test void brokenOptionalRuleValuesAndRangesAreRepaired() throws IOException {
        var file = folder.resolve("alerts.json");
        Files.writeString(file, "{\"endermiteRange\":999,\"endermiteAlert\":null,\"customTitles\":[null,{\"id\":\"same\",\"alert\":null},{\"id\":\"same\"}]}");
        var settings = new SettingsStore(file).load();
        assertEquals(128, settings.endermiteRange);
        assertNotNull(settings.endermiteAlert);
        assertEquals(2, settings.customTitles.size());
        assertNotEquals(settings.customTitles.get(0).id, settings.customTitles.get(1).id);
        assertNotNull(settings.customTitles.getFirst().alert);
        settings.endermiteRange = -1;
        settings.sanitize();
        assertEquals(4, settings.endermiteRange);
    }
}
