package dev.profittracker;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GemSessionTest {
    private static GemPrices prices() {
        return new GemPrices(Map.of(
                Gemstone.RUBY, new GemPrices.Sale(GemPrices.Route.FLAWLESS_OFFER, 640_000, 6_400),
                Gemstone.JADE, new GemPrices.Sale(GemPrices.Route.FLAWLESS_OFFER, 1_280_000, 6_400)), 2_500_000);
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
        assertEquals(1_280_000, session.value(prices(), false).orElseThrow());
        assertEquals(6_400, Gemstone.FLAWED_PER_FLAWLESS);
    }

    @Test void smallPristineProcsAreNotRoundedDownToWholeFlawlessGems() {
        var session = new GemSession(); session.start(0);
        session.pristine(ruby(1), 0);
        assertEquals(100, session.value(prices(), false).orElseThrow());
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
        assertEquals(0, session.recentValue(prices(), false, 180_000).orElseThrow());
        assertEquals(10_000, session.value(prices(), false).orElseThrow());
        session.pristine(ruby(20), 1_500);
        session.tick(2_000, true, true, true);
        assertEquals(1_000, session.rateMillis());
        assertEquals(2_000, session.recentValue(prices(), false, 180_000).orElseThrow());
        assertEquals(12_000, session.value(prices(), false).orElseThrow());
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
        session.tick(18_000, true, false, true);
        assertFalse(session.glossy(2, 18_000)); // No recent mining.
        session.tick(18_100, true, true, true);
        assertTrue(session.glossy(1, 18_100));
        session.tick(18_200, false, false, true); // Inventory open.
        assertFalse(session.glossy(1, 18_200));
        assertEquals(3, session.glossy());
    }

    @Test void delayedGlossyUsesSackGraceWithoutExtendingTheMiningTimerGrace() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, false, false);
        session.pristine(new GemMessages.Pristine(Gemstone.JADE, 32), 1_000);
        session.tick(6_000, true, false, false);
        assertFalse(session.recentlyMining(6_000));
        // The uploaded log contains a glossy notification five seconds after the last pristine.
        var count = GemMessages.glossy("[SkyOcean] Sack Notification | Glossy Gemstone x2", GemMessages.DEFAULT_GLOSSY_FORMAT).orElseThrow();
        assertTrue(session.glossy(count, 6_000));
        assertEquals(2, session.glossy());
        assertEquals(5_006_400, session.value(prices(), true).orElseThrow());
        assertFalse(session.glossy(1, 11_001));
        session.pause(11_100);
        assertFalse(session.glossy(1, 11_200));
    }

    @Test void commandQuarantineStillRejectsDelayedGlossyAfterTheLongerGrace() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, true, false);
        session.tick(6_000, true, true, false);
        session.interrupt(6_000);
        session.tick(7_000, true, true, false);
        assertFalse(session.glossy(2, 10_000));
        session.tick(11_100, true, false, false);
        assertTrue(session.glossy(1, 11_100));
        assertEquals(1, session.glossy());
    }

    @Test void optionalGlossyValueAndItsResetBaselineAreIndependentOfPristine() {
        var session = new GemSession(); session.start(0);
        session.tick(6_000, true, true, true);
        session.pristine(ruby(20), 6_000);
        session.glossy(2, 6_000);
        assertEquals(2_000, session.value(prices(), false).orElseThrow());
        assertEquals(5_002_000, session.value(prices(), true).orElseThrow());
        session.resetRate(6_000);
        assertEquals(0, session.recentValue(prices(), true, 180_000).orElseThrow());
        session.glossy(1, 6_100);
        assertEquals(2_500_000, session.recentValue(prices(), true, 180_000).orElseThrow());
    }

    @Test void missingRequiredPricesStayUnavailableInsteadOfInventingValue() {
        var session = new GemSession(); session.start(0);
        session.pristine(ruby(20), 0);
        assertTrue(session.value(null, false).isEmpty());
        var missing = new GemPrices(Map.of(), Double.NaN);
        assertTrue(session.value(missing, false).isEmpty());
        session.resetRate(1_000);
        assertEquals(0, session.recentValue(missing, false, 180_000).orElseThrow());
    }

    @Test void currentPricesRevalueBothTotalsAndTheRateWindow() {
        var session = new GemSession(); session.start(0);
        session.pristine(ruby(64), 0);
        var higher = new GemPrices(Map.of(Gemstone.RUBY, new GemPrices.Sale(GemPrices.Route.FLAWLESS_OFFER, 1_280_000, 6_400)), 1);
        assertEquals(6_400, session.value(prices(), false).orElseThrow());
        assertEquals(12_800, session.value(higher, false).orElseThrow());
    }

    @Test void rollingRateWaitsForAFullWindowThenExpiresOldGainsWithoutChangingTotals() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, false, false);
        session.tick(1_000, true, false, false);
        session.pristine(ruby(100), 1_000);
        assertTrue(session.coinsPerHour(prices(), false, 3).isEmpty());
        session.tick(90_000, true, false, false);
        session.pristine(ruby(50), 90_000);
        session.tick(179_999, true, false, false);
        assertTrue(session.coinsPerHour(prices(), false, 3).isEmpty());
        session.tick(180_000, true, false, false);
        assertEquals(300_000, session.coinsPerHour(prices(), false, 3).orElseThrow());
        session.tick(240_000, true, false, false);
        assertEquals(100_000, session.coinsPerHour(prices(), false, 3).orElseThrow());
        assertEquals(15_000, session.value(prices(), false).orElseThrow());
        session.tick(300_000, true, false, false);
        assertEquals(0, session.coinsPerHour(prices(), false, 3).orElseThrow());
    }

    @Test void oneSecondProcAndGlossyDropDoNotBecomeOneSecondHourlyProjections() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, true, false);
        session.tick(6_000, true, true, false);
        session.pristine(ruby(32), 6_000);
        session.glossy(1, 6_000);
        assertTrue(session.coinsPerHour(prices(), true, 3).isEmpty());
        session.tick(180_000, true, false, false);
        assertEquals(50_064_000, session.coinsPerHour(prices(), true, 3).orElseThrow());
        assertEquals(64_000, session.coinsPerHour(prices(), false, 3).orElseThrow());
    }

    @Test void resetWarmsUpAgainAndPauseFreezesTheRollingClock() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, false, false);
        session.tick(1_000, true, false, false);
        session.pristine(ruby(100), 1_000);
        session.tick(60_000, true, false, false);
        assertEquals(600_000, session.coinsPerHour(prices(), false, 1).orElseThrow());
        session.pause(60_000);
        session.tick(600_000, true, false, false);
        assertEquals(600_000, session.coinsPerHour(prices(), false, 1).orElseThrow());
        session.resetRate(600_000);
        assertTrue(session.coinsPerHour(prices(), false, 1).isEmpty());
        assertEquals(10_000, session.value(prices(), false).orElseThrow());
        session.start(600_000);
        session.tick(600_000, true, false, false);
        session.tick(660_000, true, false, false);
        assertEquals(0, session.coinsPerHour(prices(), false, 1).orElseThrow());
    }

    @Test void roughGainsUseEightyRoughPerFlawedAndKeepPristineCountsSeparate() {
        var session = new GemSession(); session.start(0);
        assertFalse(session.rough(Map.of(Gemstone.RUBY, 80L), 1_000));
        session.tick(6_000, true, true, false);
        session.pristine(ruby(20), 6_000);
        assertTrue(session.rough(Map.of(Gemstone.RUBY, 80L), 6_000));
        assertEquals(2_100, session.value(prices(), false).orElseThrow());
        assertEquals(20, session.flawedTotal());
        assertEquals(80, session.roughTotal());
        session.interrupt(6_100);
        session.tick(7_000, true, true, false);
        assertFalse(session.rough(Map.of(Gemstone.RUBY, 80L), 7_000));
        session.pause(12_000);
        assertFalse(session.rough(Map.of(Gemstone.RUBY, 80L), 12_100));
    }

    @Test void longSessionsKeepOnlyTenMinutesOfPerSecondBuckets() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, false, false);
        for (int second = 1; second <= 6_000; second++) {
            session.tick(second * 1_000L, true, false, false);
            session.pristine(ruby(1), second * 1_000L);
        }
        assertTrue(session.bucketCount() <= 601);
        assertEquals(6_000, session.flawedTotal());
        assertEquals(600_000, session.value(prices(), false).orElseThrow());
        assertTrue(session.coinsPerHour(prices(), false, 10).isPresent());
    }

    @Test void inventoryQuarantineSkipsDelayedTransfersWithoutStoppingRunningTime() {
        var session = new GemSession(); session.start(0);
        session.tick(0, true, true, false);
        session.tick(6_000, true, true, false);
        session.quarantineSacks(6_000); // Inventory opens.
        session.tick(20_000, true, false, false);
        assertEquals(20_000, session.rateMillis());
        session.quarantineSacks(20_000); // Inventory closes.
        session.tick(21_000, true, true, false);
        assertFalse(session.glossy(1, 21_000));
        assertFalse(session.rough(Map.of(Gemstone.RUBY, 80L), 21_000));
        session.tick(25_100, true, true, false);
        assertTrue(session.rough(Map.of(Gemstone.RUBY, 80L), 25_100));
        assertEquals(25_100, session.rateMillis());
    }
}
