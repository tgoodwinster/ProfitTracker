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
}
