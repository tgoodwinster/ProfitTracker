package dev.profittracker;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookCombinerTest {
    private static BookCombinerSettings profile() {
        var p = new BookCombinerSettings();
        p.server = "example.org";
        p.menuTitle = "Anvil";
        p.rows = 6;
        p.firstSlot = 29;
        p.secondSlot = 33;
        p.outputSlot = 13;
        p.confirmSlot = 22;
        p.confirmItem = "minecraft:anvil";
        p.enchantment = "skyblock:sharpness";
        return p;
    }

    private static BookCombiner.Cell book(int level) {
        return new BookCombiner.Cell(new BookCombiner.Book("skyblock:sharpness", level), 1, "");
    }

    private static class Server {
        final BookCombinerSettings p = profile();
        final ArrayList<BookCombiner.Cell> slots = new ArrayList<>(java.util.Collections.nCopies(90, BookCombiner.Cell.EMPTY));
        final BookCombiner combiner = new BookCombiner(p, 0);
        long revision;
        final ArrayList<Long> versions = new ArrayList<>(java.util.Collections.nCopies(90, 0L));
        long now;
        boolean cursorEmpty = true;
        Server() { update(22, new BookCombiner.Cell(null, 1, "minecraft:anvil\nAnvil")); }
        int click() { return combiner.tick(new BookCombiner.Snapshot(slots, revision, cursorEmpty, versions), now); }
        void update(int slot, BookCombiner.Cell item) { slots.set(slot, item); versions.set(slot, ++revision); }
        void confirm(int click) {
            if (click == p.confirmSlot) {
                assertFalse(combiner.shiftClick(), "Anvil must be a normal click");
                update(p.outputSlot, book(slots.get(p.firstSlot).book().level() + 1));
            } else if (click == p.outputSlot) {
                assertTrue(combiner.shiftClick());
                var result = slots.get(p.outputSlot);
                update(p.firstSlot, BookCombiner.Cell.EMPTY);
                update(p.secondSlot, BookCombiner.Cell.EMPTY);
                update(p.outputSlot, BookCombiner.Cell.EMPTY);
                for (int i = 54; i < 90; i++) if (slots.get(i).count() == 0) { update(i, result); return; }
                fail("No output space");
            } else {
                int input = slots.get(p.firstSlot).book() == null ? p.firstSlot : p.secondSlot;
                var item = slots.get(click);
                update(click, BookCombiner.Cell.EMPTY);
                update(input, item);
            }
        }
    }

    @Test void sixteenTierOneBooksBecomeOneTierFiveAtOneClickPerDelay() {
        var s = new Server();
        for (int i = 54; i < 70; i++) s.update(i, book(1));
        // Existing tier V and unrelated enchants must never be touched.
        s.update(89, book(5));
        var unrelated = new BookCombiner.Cell(new BookCombiner.Book("skyblock:efficiency", 1), 1, "");
        s.update(88, unrelated);
        int clicks = 0;
        long last = 0;
        for (int t = 0; t < 2_000 && s.combiner.running(); t++) {
            s.now += 50;
            int click = s.click();
            if (click < 0) continue;
            assertTrue(s.now - last >= 750);
            last = s.now;
            assertNotEquals(88, click);
            assertNotEquals(89, click);
            clicks++;
            s.confirm(click);
            assertEquals(-1, s.click(), "No second click in the same tick");
        }
        assertFalse(s.combiner.running());
        assertEquals(60, clicks);
        assertEquals(15, s.combiner.completed());
        assertEquals(2, s.slots.subList(54, 90).stream().filter(c -> book(5).equals(c)).count());
        assertEquals(unrelated, s.slots.get(88));
    }

    @Test void noSecondClickWithoutServerConfirmationAndNoRetryAfterTimeout() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750;
        assertEquals(54, s.click());
        s.now = 2_000;
        assertEquals(-1, s.click());
        s.now = 8_750;
        assertEquals(-1, s.click());
        assertFalse(s.combiner.running());
        assertTrue(s.combiner.status().contains("Server did not confirm"));
        s.confirm(54);
        s.now = 20_000;
        assertEquals(-1, s.click());
    }

    @Test void partialPacketsAndDecorativeUpdatesCannotConfirmAMove() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; assertEquals(54, s.click());
        s.update(0, new BookCombiner.Cell(null, 1, "pane"));
        s.update(29, book(1));
        s.now = 1_500;
        assertEquals(-1, s.click(), "Inventory must also confirm source removal");
        s.update(54, BookCombiner.Cell.EMPTY);
        assertEquals(55, s.click());
    }

    @Test void optimisticLocalChangesWithoutAnIncomingPacketAreNotConfirmation() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; assertEquals(54, s.click());
        s.slots.set(54, BookCombiner.Cell.EMPTY);
        s.slots.set(29, book(1));
        s.now = 1_500;
        assertEquals(-1, s.click());
    }

    @Test void wrongOutputTierIsNeverClicked() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; s.confirm(s.click());
        s.now = 1_500; s.confirm(s.click());
        s.now = 2_250; assertEquals(22, s.click());
        s.update(13, book(3));
        s.now = 3_000;
        assertEquals(-1, s.click());
        assertEquals(0, s.combiner.completed());
        s.now = 10_250;
        assertEquals(-1, s.click());
        assertFalse(s.combiner.running());
    }

    @Test void previewBeforeConfirmationAndUnrelatedUpdatesDoNotPermitCollection() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; s.confirm(s.click());
        s.now = 1_500; s.confirm(s.click());
        s.update(13, book(2)); // Some servers show a preview before the anvil is pressed.
        s.now = 2_250; assertEquals(22, s.click());
        assertFalse(s.combiner.shiftClick());
        s.update(0, new BookCombiner.Cell(null, 1, "unrelated decoration"));
        s.now = 3_000;
        assertEquals(-1, s.click());
        s.update(13, book(2)); // The server confirms the actual result after the click.
        assertEquals(13, s.click());
        assertTrue(s.combiner.shiftClick());
    }

    @Test void serverMayConsumeInputsWhenTheAnvilIsPressed() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; s.confirm(s.click());
        s.now = 1_500; s.confirm(s.click());
        s.now = 2_250; s.confirm(s.click());
        s.update(29, BookCombiner.Cell.EMPTY);
        s.update(33, BookCombiner.Cell.EMPTY);
        s.now = 3_000;
        assertEquals(13, s.click());
        s.confirm(13);
        s.now = 3_750;
        assertEquals(-1, s.click());
        assertEquals(1, s.combiner.completed());
    }

    @Test void waitsForPredictedCursorToClearWithoutClickingOrStoppingEarly() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; s.confirm(s.click());
        s.now = 1_500; s.confirm(s.click());
        s.now = 2_250; s.confirm(s.click());
        s.now = 3_000;
        assertEquals(-1, s.combiner.tick(new BookCombiner.Snapshot(s.slots, s.revision, true, s.versions, false), s.now));
        assertTrue(s.combiner.running());
        assertEquals(13, s.click());
    }

    @Test void changedConfirmationButtonStopsBeforeNormalClick() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; s.confirm(s.click());
        s.now = 1_500; s.confirm(s.click());
        s.update(22, new BookCombiner.Cell(null, 1, "minecraft:barrier\nNot enough XP"));
        s.now = 2_250;
        assertEquals(-1, s.click());
        assertFalse(s.combiner.running());
    }

    @Test void resultMustArriveInInventoryBeforeAnotherCycle() {
        var s = new Server();
        for (int i = 54; i < 58; i++) s.update(i, book(1));
        s.now = 750; s.confirm(s.click());
        s.now = 1_500; s.confirm(s.click());
        s.now = 2_250; s.confirm(s.click());
        s.now = 3_000; assertEquals(13, s.click());
        s.update(29, BookCombiner.Cell.EMPTY);
        s.update(33, BookCombiner.Cell.EMPTY);
        s.update(13, BookCombiner.Cell.EMPTY);
        s.now = 3_750;
        assertEquals(-1, s.click());
        s.update(54, book(2));
        assertEquals(-1, s.click());
        s.now += 50;
        assertEquals(56, s.click());
        assertEquals(1, s.combiner.completed());
    }

    @Test void filledInputsAndExistingResultAreNeverAdopted() {
        for (int slot : new int[]{29, 33, 13}) {
            var s = new Server();
            s.update(54, book(1)); s.update(55, book(1));
            s.update(slot, book(slot == 13 ? 2 : 1));
            s.now = 750;
            assertEquals(-1, s.click());
            assertFalse(s.combiner.running());
        }
    }

    @Test void confirmedInputClearingCanAcknowledgeAnOmittedOutputEcho() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750; s.confirm(s.click());
        s.now = 1_500; s.confirm(s.click());
        s.now = 2_250; s.confirm(s.click());
        s.now = 3_000; assertEquals(13, s.click());
        s.update(54, book(2));
        // Local prediction alone must not confirm result collection.
        s.now = 3_750;
        assertEquals(-1, s.combiner.tick(new BookCombiner.Snapshot(s.slots, s.revision, true, s.versions, true, BookCombiner.Cell.EMPTY), s.now));
        assertEquals(0, s.combiner.completed());
        s.update(29, BookCombiner.Cell.EMPTY); s.update(33, BookCombiner.Cell.EMPTY);
        assertEquals(-1, s.combiner.tick(new BookCombiner.Snapshot(s.slots, s.revision, true, s.versions, true, BookCombiner.Cell.EMPTY), s.now));
        assertEquals(1, s.combiner.completed());
    }

    @Test void stackedBooksAndMismatchedTiersAreNotMoved() {
        var s = new Server();
        s.update(54, new BookCombiner.Cell(book(1).book(), 16, ""));
        s.update(55, book(2));
        s.now = 750;
        assertEquals(-1, s.click());
        assertFalse(s.combiner.running());
    }

    @Test void learnsIdleDecorationsAndStopsIfTheyChange() {
        var s = new Server();
        s.p.firstIdle = "input placeholder";
        s.update(29, new BookCombiner.Cell(null, 1, "different item"));
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 750;
        assertEquals(-1, s.click());
        assertFalse(s.combiner.running());
    }

    @Test void cursorOrLayoutChangesStopWithoutClicks() {
        var s = new Server();
        s.cursorEmpty = false;
        assertEquals(-1, s.click());
        assertFalse(s.combiner.running());
        var other = new Server();
        other.slots.removeLast();
        assertEquals(-1, other.click());
        assertFalse(other.combiner.running());
    }

    @Test void longClientPauseDoesNotSendACatchUpBurst() {
        var s = new Server();
        s.update(54, book(1)); s.update(55, book(1));
        s.now = 60_000;
        int click = s.click();
        assertEquals(54, click);
        s.confirm(click);
        assertEquals(-1, s.click());
        s.now += 749;
        assertEquals(-1, s.click());
        s.now++;
        assertEquals(55, s.click());
    }

    @Test void delayIsBoundedAndInvalidProfilesStayUnconfigured() {
        var p = new BookCombinerSettings();
        p.delayMs = 1; p.maxLevel = 100;
        p.sanitize();
        assertEquals(500, p.delayMs);
        assertEquals(10, p.maxLevel);
        assertFalse(p.configured());
        p = profile();
        assertTrue(p.configured());
        p.outputSlot = p.firstSlot;
        assertFalse(p.configured());
    }
}
