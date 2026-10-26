package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoalSessionTest {
    private static final CoalStock EMPTY = CoalStock.ZERO;

    private CoalSession begin(CoalStock existing) {
        var session = new CoalSession();
        session.start(0, existing);
        session.tick(10_000, existing, true, false);
        return session;
    }

    private void settle(CoalSession session, CoalStock stock) {
        for (long t = 10_100; t <= 17_000; t += 50) session.tick(t, stock, true, false);
    }

    @Test void existingInventoryIsNeverProfit() {
        var existing = new CoalStock(900, 45, 2);
        var session = begin(existing);
        settle(session, existing);
        assertEquals(0, session.coal());
    }

    @Test void rawInventoryGainsRequireMiningAndConfirmation() {
        var session = begin(EMPTY);
        session.tick(10_050, new CoalStock(64, 0, 0), true, true);
        assertEquals(0, session.coal());
        settle(session, new CoalStock(64, 0, 0));
        assertEquals(64, session.coal());
    }

    @Test void inventoryGainsWithoutMiningAreIgnored() {
        var session = begin(EMPTY);
        session.tick(10_050, new CoalStock(64, 0, 0), true, false);
        settle(session, new CoalStock(64, 0, 0));
        assertEquals(0, session.coal());
    }

    @Test void compactionOfExistingCoalIsNotNewProfit() {
        var session = begin(new CoalStock(160, 0, 0));
        session.tick(10_050, new CoalStock(0, 1, 0), true, true);
        settle(session, new CoalStock(0, 1, 0));
        assertEquals(0, session.coal());
    }

    @Test void compactorCrossingBaselineCountsOnlyNewCoal() {
        var session = begin(new CoalStock(159, 0, 0));
        session.tick(10_050, new CoalStock(0, 1, 0), true, true);
        settle(session, new CoalStock(0, 1, 0));
        assertEquals(1, session.coal());
        assertEquals(1, session.shares().enchanted());
        assertEquals(10, session.shares().valueAsShares(2, 1600, 256000), 0.0001);
    }

    @Test void compactedBlocksUse25600Coal() {
        var session = begin(new CoalStock(0, 159, 0));
        session.tick(10_050, new CoalStock(0, 0, 1), true, true);
        settle(session, new CoalStock(0, 0, 1));
        assertEquals(160, session.coal());
    }

    @Test void sackOnlyMiningWorksWithoutInventoryGrowth() {
        var session = begin(EMPTY);
        session.tick(10_050, EMPTY, true, true);
        session.sackChange(10_100, EMPTY, new CoalStock(1000, 2, 0), true);
        settle(session, EMPTY);
        assertEquals(1320, session.coal());
    }

    @Test void inventoryToSackTransferDoesNotCountTwice() {
        var session = begin(new CoalStock(64, 0, 0));
        session.tick(10_050, EMPTY, true, true);
        session.sackChange(10_100, EMPTY, new CoalStock(64, 0, 0), true);
        settle(session, EMPTY);
        assertEquals(0, session.coal());
    }

    @Test void compactedInventoryMovingToEnchantedSackCountsOnlyMining() {
        var session = begin(new CoalStock(159, 0, 0));
        session.tick(10_050, EMPTY, true, true);
        session.sackChange(10_100, EMPTY, new CoalStock(0, 1, 0), true);
        settle(session, EMPTY);
        assertEquals(1, session.coal());
    }

    @Test void delayedWithdrawalMessageDiscardsInventoryGain() {
        var session = begin(EMPTY);
        session.tick(10_050, new CoalStock(640, 0, 0), true, true);
        session.tick(14_000, new CoalStock(640, 0, 0), true, false);
        session.sackChange(14_100, new CoalStock(640, 0, 0), new CoalStock(-640, 0, 0), true);
        settle(session, new CoalStock(640, 0, 0));
        assertEquals(0, session.coal());
    }

    @Test void withdrawalsBeforeInventoryUpdateAreAlsoIgnored() {
        var session = begin(EMPTY);
        session.tick(10_050, EMPTY, true, true);
        session.sackChange(10_100, EMPTY, new CoalStock(-100, 0, 0), true);
        settle(session, new CoalStock(100, 0, 0));
        assertEquals(0, session.coal());
    }

    @Test void missingHoverAndTruncatedMessagesDiscardAmbiguousTail() {
        var session = begin(EMPTY);
        session.tick(10_050, new CoalStock(64, 0, 0), true, true);
        session.sackChange(10_100, new CoalStock(64, 0, 0), EMPTY, false);
        settle(session, new CoalStock(64, 0, 0));
        assertEquals(0, session.coal());
    }

    @Test void menusDiscardUnconfirmedGainsAndRebaseline() {
        var session = begin(EMPTY);
        session.tick(10_050, new CoalStock(64, 0, 0), true, true);
        session.tick(11_000, new CoalStock(900, 0, 0), false, false);
        settle(session, new CoalStock(900, 0, 0));
        assertEquals(0, session.coal());
    }

    @Test void pauseStopsBothEarningsAndDuration() {
        var session = begin(EMPTY);
        session.tick(10_050, new CoalStock(64, 0, 0), true, true);
        settle(session, new CoalStock(64, 0, 0));
        session.pause(17_001, new CoalStock(64, 0, 0));
        long duration = session.elapsedMillis();
        session.tick(18_000, new CoalStock(900, 0, 0), true, true);
        session.sackChange(18_100, new CoalStock(900, 0, 0), new CoalStock(1000, 0, 0), true);
        assertEquals(64, session.coal());
        assertEquals(duration, session.elapsedMillis());
    }

    @Test void resetClearsEverythingAndPreservesRunningState() {
        var session = begin(EMPTY);
        session.tick(10_050, new CoalStock(64, 0, 0), true, true);
        settle(session, new CoalStock(64, 0, 0));
        session.reset(17_001, new CoalStock(64, 0, 0));
        assertEquals(0, session.coal());
        assertEquals(CoalStock.ZERO, session.shares());
        assertEquals(0, session.elapsedMillis());
        assertTrue(session.running());
    }

    @Test void continuousMiningConfirmsOldGainsWithoutWaitingForIdle() {
        var session = begin(EMPTY);
        for (int i = 1; i <= 200; i++) {
            session.tick(10_000 + i * 50L, new CoalStock(i, 0, 0), true, true);
        }
        assertTrue(session.coal() >= 79 && session.coal() <= 80);
    }
}
