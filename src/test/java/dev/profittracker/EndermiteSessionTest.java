package dev.profittracker;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndermiteSessionTest {
    @Test void onlyConfirmedDeathsAfterOwnHitsCountAndDuplicateSignalsDoNot() {
        var s = new EndermiteSession(); var id = UUID.randomUUID();
        s.tick(0, true); s.hit(id, 100);
        assertEquals(0, s.kills()); // A hit alone, disappearance or leaving range is not a kill.
        assertTrue(s.death(id, 200, false));
        assertTrue(s.personalDeath());
        assertFalse(s.death(id, 250, false));
        assertEquals(1, s.kills());
    }
    @Test void otherPlayersLastDamageAndExpiredHitsDoNotBecomePersonalKills() {
        var s = new EndermiteSession(); s.tick(0, true);
        var other = UUID.randomUUID(); s.hit(other, 100); s.damage(other, false, 200);
        assertFalse(s.death(other, 300, false));
        var expired = UUID.randomUUID(); s.hit(expired, 1_000);
        assertFalse(s.death(expired, 6_001, false));
        assertFalse(s.death(UUID.randomUUID(), 6_100, false));
        assertEquals(0, s.kills());
    }
    @Test void nearbyModeStillRequiresADeathAndKeepsPersonalAttributionSeparate() {
        var s = new EndermiteSession(); s.tick(0, true);
        assertTrue(s.death(UUID.randomUUID(), 100, true));
        assertFalse(s.personalDeath());
        assertEquals(1, s.kills());
    }
    @Test void clockPausesOutsideTheEndAndResetDoesNotTouchLifetimeBestiary() {
        var s = new EndermiteSession(); s.tick(0, true);
        var id = UUID.randomUUID(); s.hit(id, 10); s.death(id, 20, false);
        assertEquals("--", s.rate());
        s.tick(60_000, true); assertEquals("60", s.rate());
        s.tick(61_000, false); s.tick(600_000, false); assertEquals(60_000, s.elapsedMillis());
        s.clearEvidence(); assertEquals(1, s.kills());
        s.reset(600_000); assertEquals(0, s.kills()); assertEquals(0, s.elapsedMillis());
    }
}
