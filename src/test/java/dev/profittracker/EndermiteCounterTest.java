package dev.profittracker;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndermiteCounterTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test void countsUniqueLivingSnapshotRatherThanAddingRepeatedObservations() {
        var counter = new EndermiteCounter();
        assertFalse(counter.update(List.of(a, a, b), 3, 0));
        assertEquals(2, counter.count());
        counter.update(List.of(a, b), 3, 250);
        assertEquals(2, counter.count());
        counter.update(List.of(b), 3, 500);
        assertEquals(1, counter.count());
        counter.update(List.of(), 3, 750);
        assertEquals(0, counter.count());
    }

    @Test void unknownCapShowsCountWithoutInventingAnAlert() {
        var counter = new EndermiteCounter();
        assertFalse(counter.update(List.of(a, b, c), 0, 0));
        assertEquals(3, counter.count());
        assertFalse(counter.atTarget());
    }

    @Test void alertsOnceAtOrAboveTheConfiguredTarget() {
        var counter = new EndermiteCounter();
        assertFalse(counter.update(List.of(a), 2, 0));
        assertTrue(counter.update(List.of(a, b), 2, 250));
        assertTrue(counter.atTarget());
        assertFalse(counter.update(List.of(a, b), 2, 500));
        assertFalse(counter.update(List.of(a, b, c), 2, 750));
    }

    @Test void briefRangeJitterDoesNotRepeatTheTitle() {
        var counter = new EndermiteCounter();
        assertTrue(counter.update(List.of(a, b), 2, 0));
        counter.update(List.of(a), 2, 250);
        assertFalse(counter.atTarget());
        assertFalse(counter.update(List.of(a, b), 2, 500));
        assertTrue(counter.atTarget());
    }

    @Test void killingMobsRearmsTheNextMiningCycle() {
        var counter = new EndermiteCounter();
        assertTrue(counter.update(List.of(a, b), 2, 0));
        counter.update(List.of(), 2, 250);
        counter.update(List.of(), 2, 1250);
        assertTrue(counter.update(List.of(a, c), 2, 1500));
    }

    @Test void worldChangeClearsCountAndAlertState() {
        var counter = new EndermiteCounter();
        counter.update(List.of(a, b), 2, 0);
        counter.clear();
        assertEquals(0, counter.count());
        assertFalse(counter.atTarget());
        assertTrue(counter.update(List.of(c), 1, 1000));
    }

    @Test void changingThresholdUsesTheNewTargetImmediately() {
        var counter = new EndermiteCounter();
        assertFalse(counter.update(List.of(a, b), 3, 0));
        assertTrue(counter.update(List.of(a, b), 2, 250));
        assertFalse(counter.update(List.of(a, b), 0, 500));
        assertFalse(counter.atTarget());
    }
}
