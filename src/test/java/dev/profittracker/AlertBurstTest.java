package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlertBurstTest {
    @Test void fivePulsesAreSeparatedAndFinishAfterFiveSeconds() {
        var burst = new AlertBurst(); burst.start(0);
        for (int i=0;i<5;i++) { assertTrue(burst.tick(i*1000)); assertFalse(burst.tick(i*1000+100)); }
        assertTrue(burst.active()); assertFalse(burst.tick(5000)); assertTrue(burst.finished());
    }
    @Test void stalledFramesDoNotDumpCatchupSoundsOrLoseRemainingPulses() {
        var burst = new AlertBurst(); burst.start(0);
        assertTrue(burst.tick(0)); assertTrue(burst.tick(20_000)); assertFalse(burst.tick(20_001));
        assertTrue(burst.tick(21_000)); assertTrue(burst.tick(22_000)); assertTrue(burst.tick(23_000));
        burst.tick(24_000); assertTrue(burst.finished());
    }
}
