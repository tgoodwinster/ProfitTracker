package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisitorTimerTest {
    private VisitorSettings settings() { var s = new VisitorSettings(); s.enabled = true; s.capacity = 10; s.intervalSeconds = 60; return s; }
    @Test void unsetIntervalCannotClaimVisitorsOrReadyState() {
        var settings = new VisitorSettings(); settings.enabled = true;
        var timer = new VisitorTimer(settings); timer.correct(0, 1000);
        assertFalse(timer.resume(2000)); assertEquals(0, timer.count(999999)); assertFalse(timer.ready(999999));
    }
    @Test void estimatesEachArrivalAndCapsAtConfiguredCapacityAcrossRestart() {
        var settings = settings(); var timer = new VisitorTimer(settings); timer.correct(0, 1000);
        assertEquals(0, timer.count(60_999)); assertEquals(1, timer.count(61_000));
        assertEquals(10, timer.count(601_000)); assertTrue(timer.ready(601_000));
        assertEquals(10, new VisitorTimer(settings).count(9_000_000));
        settings.notified = true; assertFalse(timer.ready(9_000_000));
    }
    @Test void pauseKeepsPartialIntervalAndOfflineResumeDoesNotLoseProgress() {
        var s = settings(); var timer = new VisitorTimer(s); timer.correct(0, 1000);
        assertTrue(timer.pause(31_000)); assertEquals(30_000, s.partialMillis);
        assertEquals(0, timer.count(500_000)); assertTrue(timer.resume(500_000));
        assertEquals(0, timer.count(529_999)); assertEquals(1, timer.count(530_000));
        assertEquals(9 * 60_000, timer.remaining(530_000));
    }
    @Test void increasingFullCapacityDoesNotBankImaginaryOverflowVisitors() {
        var s = settings(); var timer = new VisitorTimer(s); timer.correct(0, 1000);
        timer.reconfigure(20, 60, 9_000_000);
        assertEquals(10, timer.count(9_000_000)); assertEquals(11, timer.count(9_060_000));
        timer.correct(0, 9_060_001); assertFalse(s.notified); assertEquals(0, timer.count(9_060_001));
    }
    @Test void backwardsSystemClockNeverInventsArrivals() {
        var s = settings(); var timer = new VisitorTimer(s); timer.correct(2, 1_000_000);
        assertEquals(2, timer.count(500_000)); assertFalse(timer.ready(500_000));
    }
    @Test void choosingAnIntervalDoesNotStartAnUnarmedTimerByItself() {
        var s=new VisitorSettings();s.enabled=true;var timer=new VisitorTimer(s);
        timer.reconfigure(10,60,1000);assertFalse(s.armed);assertFalse(s.running);
        assertTrue(timer.resume(1000));assertTrue(s.armed);assertEquals(1,timer.count(61000));
    }
}
