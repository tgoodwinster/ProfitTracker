package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlertTest {
    @Test void titleStaysForWholeDurationAfterTriggerAndExpiresExactlyOnce() {
        var style = new AlertStyle();
        var timeline = new TitleTimeline();
        timeline.show(style, "endermites", 100);
        assertEquals("KILL", timeline.current(101).text);
        assertNotNull(timeline.current(3099));
        assertNull(timeline.current(3100));
        assertNull(timeline.current(3200));
    }

    @Test void titleSnapshotsSettingsAndMostRecentTriggerReplacesIt() {
        var style = new AlertStyle();
        var timeline = new TitleTimeline();
        timeline.show(style, "endermites", 0);
        style.text = "ABILITY READY";
        assertEquals("KILL", timeline.current(100).text);
        timeline.show(style, "chat", 200);
        timeline.clearSource("endermites");
        assertEquals("ABILITY READY", timeline.current(300).text);
        timeline.clear();
        assertNull(timeline.current(301));
    }

    @Test void titleUsesConfiguredColorAndSanitizesInvalidSavedFields() {
        var style = new AlertStyle();
        style.color = "#aabbcc";
        style.scale = Double.NaN;
        style.seconds = -99;
        style.volume = 2;
        style.sound = null;
        style.position = null;
        style.sanitize();
        assertEquals(0xFFAABBCC, style.argb());
        assertEquals(4, style.scale);
        assertEquals(0.5, style.seconds);
        assertEquals(1, style.volume);
        assertEquals(AlertStyle.Sound.DING, style.sound);
        assertNotNull(style.position);
    }

    @Test void longTitlesFitOnScreenAndStayAtSelectedEdgeAfterResize() {
        var style = new AlertStyle();
        style.scale = 8;
        style.position.x = 1;
        style.position.y = 1;
        var box = AlertLayout.calculate(style, 500, 9, 320, 240);
        assertTrue(box.width() <= 320);
        assertTrue(box.height() <= 240);
        assertEquals(320, box.x() + box.width());
        assertEquals(240, box.y() + box.height());
        assertEquals(8, style.scale); // Automatic fitting does not overwrite the preference.
    }
}
