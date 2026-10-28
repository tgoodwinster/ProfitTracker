package dev.profittracker;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GemSessionTest {
    private static BazaarPrices prices() {
        return new BazaarPrices(10, 100, 1_000, 100_000,
                Map.of(Gemstone.RUBY, 640_000d, Gemstone.JADE, 1_280_000d), 2_500_000);
    }
    private static GemMessages.Pristine ruby(long count) { return new GemMessages.Pristine(Gemstone.RUBY, count); }

    @Test void initiallyPausedAndPausedSessionsDoNotAcceptGains() {
        var session = new GemSession();
        assertFalse(session.running());
        assertFalse(session.pristine(ruby(20), 0));
        session.start(0);
        assertTrue(session.pristine(ruby(20), 100));
        session.pause(200);
        assertFalse(session.pristine(ruby(20), 300));
        assertFalse(session.glossy(2, 6_000));
        assertEquals(20, session.flawedTotal());
    }

    @Test void differentGemstonesUseTheirOwnFlawlessPricesAndFractions() {
        var session = new GemSession(); session.start(0);
        session.pristine(ruby(6_400), 0);
        session.pristine(new GemMessages.Pristine(Gemstone.JADE, 3_200), 100);
        assertEquals(2, session.procs());
        assertEquals(9_600, session.flawedTotal());
        assertEquals(1_280_000, session.value(prices(), false, false).orElseThrow());
        assertEquals(6_400, Gemstone.FLAWED_PER_FLAWLESS);
    }

    @Test void smallPristineProcsAreNotRoundedDownToWholeFlawlessGems() {
        var session = new GemSession(); session.start(0);
        session.pristine(ruby(1), 0);
        assertEquals(100, session.value(prices(), false, false).orElseThrow());
    }

    @Test void miningClockStopsAfterGraceAndNeverAddsTimeForMenusOrOffline() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, true, true);
        session.tick(1_000, true, true, true);
        session.tick(2_000, true, false, true);
        session.tick(3_000, true, false, true);
        session.tick(4_000, true, false, true);
        session.tick(5_000, false, false, true);
        assertEquals(3_000, session.rateMillis());
        assertFalse(session.active());
        session.tick(6_000, true, true, true);
        assertEquals(3_000, session.rateMillis());
        session.tick(7_000, true, true, true);
        assertEquals(4_000, session.rateMillis());
    }

    @Test void runningClockIncludesAllIdleTimeAndSlowFramesUntilPaused() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, false, false);
        session.tick(1_000, true, false, false);
        assertEquals(1_000, session.rateMillis());
        session.tick(600_000, true, false, false);
        assertEquals(600_000, session.rateMillis());
        session.pause(600_000);
        session.tick(601_000, true, false, false);
        assertEquals(600_000, session.rateMillis());
    }

    @Test void miningClockStillCapsLongUnobservedIntervalsAndDisconnectedTime() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, true, true);
        session.tick(600_000, true, true, true);
        assertEquals(1_000, session.rateMillis());
        session.tick(601_000, false, false, true);
        session.tick(602_000, true, true, true);
        assertEquals(1_000, session.rateMillis());
    }

    @Test void rateResetClearsTimeAndItsEarningsWhileKeepingSessionTotals() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, true, true);
        session.pristine(ruby(100), 0);
        session.tick(1_000, true, true, true);
        session.resetRate(1_000);
        assertTrue(session.running());
        assertEquals(0, session.rateMillis());
        assertEquals(1_000, session.activeMillis());
        assertEquals(0, session.value(prices(), false, true).orElseThrow());
        assertEquals(10_000, session.value(prices(), false, false).orElseThrow());
        session.pristine(ruby(20), 1_500);
        session.tick(2_000, true, true, true);
        assertEquals(1_000, session.rateMillis());
        assertEquals(2_000, session.value(prices(), false, true).orElseThrow());
        assertEquals(12_000, session.value(prices(), false, false).orElseThrow());
        session.pause(2_000);
        session.resetRate(2_100);
        assertFalse(session.running());
        assertEquals(120, session.flawedTotal());
    }

    @Test void glossyRequiresRecentMiningAndRejectsTransferWindows() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, true, true);
        assertFalse(session.glossy(1, 1_000)); // Startup quarantine.
        session.tick(6_000, true, true, true);
        assertTrue(session.glossy(2, 6_000));
        session.interrupt(6_100); // Command / server change.
        session.tick(7_000, true, true, true);
        assertFalse(session.glossy(2, 7_000));
        session.tick(12_000, true, false, true);
        assertFalse(session.glossy(2, 12_000)); // No recent mining.
        session.tick(12_100, true, true, true);
        assertTrue(session.glossy(1, 12_100));
        session.tick(12_200, false, false, true); // Inventory open.
        assertFalse(session.glossy(1, 12_200));
        assertEquals(3, session.glossy());
    }

    @Test void optionalGlossyValueAndItsResetBaselineAreIndependentOfPristine() {
        var session = new GemSession(); session.start(0);
        session.tick(6_000, true, true, true);
        session.pristine(ruby(20), 6_000);
        session.glossy(2, 6_000);
        assertEquals(2_000, session.value(prices(), false, false).orElseThrow());
        assertEquals(5_002_000, session.value(prices(), true, false).orElseThrow());
        session.resetRate(6_000);
        assertEquals(0, session.value(prices(), true, true).orElseThrow());
        session.glossy(1, 6_100);
        assertEquals(2_500_000, session.value(prices(), true, true).orElseThrow());
    }

    @Test void missingRequiredPricesStayUnavailableInsteadOfInventingValue() {
        var session = new GemSession(); session.start(0);
        session.pristine(ruby(20), 0);
        assertTrue(session.value(null, false, false).isEmpty());
        var missing = new BazaarPrices(1, 1, 1, 100, Map.of(), Double.NaN);
        assertTrue(session.value(missing, false, false).isEmpty());
        session.resetRate(1_000);
        assertEquals(0, session.value(missing, false, true).orElseThrow());
    }

    @Test void currentPricesRevalueBothTotalsAndTheRateWindow() {
        var session = new GemSession(); session.start(0);
        session.pristine(ruby(64), 0);
        var higher = new BazaarPrices(1, 1, 1, 100, Map.of(Gemstone.RUBY, 1_280_000d), 1);
        assertEquals(6_400, session.value(prices(), false, false).orElseThrow());
        assertEquals(12_800, session.value(higher, false, false).orElseThrow());
    }
}
