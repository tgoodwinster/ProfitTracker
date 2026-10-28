package dev.profittracker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Deliberately paced, server-confirmed four-click cycles. No retry or catch-up loop. */
public final class BookCombiner {
    public record Book(String enchantment, int level) { }
    public record Cell(Book book, int count, String idle) {
        public static final Cell EMPTY = new Cell(null, 0, "");
    }
    public record Snapshot(List<Cell> slots, long revision, boolean cursorEmpty, List<Long> slotRevisions,
                           boolean localCursorEmpty, Cell localOutput) {
        public Snapshot(List<Cell> slots, long revision, boolean cursorEmpty, List<Long> slotRevisions) {
            this(slots, revision, cursorEmpty, slotRevisions, cursorEmpty, null);
        }
        public Snapshot(List<Cell> slots, long revision, boolean cursorEmpty, List<Long> slotRevisions, boolean localCursorEmpty) {
            this(slots, revision, cursorEmpty, slotRevisions, localCursorEmpty, null);
        }
    }
    private enum Stage { READY, FIRST, SECOND, CONFIRM, OUTPUT }
    private final BookCombinerSettings profile;
    private final RandomGenerator delays;
    private Stage stage = Stage.READY;
    private boolean running = true;
    private String status = "Ready";
    private long nextClick;
    private long deadline;
    private long sentRevision;
    private int source;
    private int activeLevel;
    private int beforeInputs;
    private int beforeOutputs;
    private int completed;
    public static final long RESPONSE_TIMEOUT_MS = 8_000;

    public BookCombiner(BookCombinerSettings profile, long now) {
        this(profile, now, ThreadLocalRandom.current());
    }

    BookCombiner(BookCombinerSettings profile, long now, RandomGenerator delays) {
        this.profile = profile;
        this.delays = delays;
        profile.sanitize();
        activeLevel = profile.inputLevel;
        nextClick = now + nextDelay();
    }

    /** Returns one click slot, or -1. Menu cells must be authoritative; inventory must be reconciled after a server update. */
    public int tick(Snapshot view, long now) {
        if (!running) return -1;
        if (!profile.configured() || view.slots().size() != profile.rows * 9 + 36
                || view.slotRevisions().size() != view.slots().size()) {
            stop("Menu layout changed");
            return -1;
        }
        if (!view.cursorEmpty()) { stop("Clear the mouse cursor before combining"); return -1; }
        if (stage != Stage.READY && now >= deadline) {
            stop("Server did not confirm the move; check the menu before restarting");
            return -1;
        }
        if (stage != Stage.READY && view.revision() <= sentRevision) return -1;
        int inputCount = count(view, activeLevel);
        int outputCount = count(view, activeLevel + 1);
        if (stage == Stage.OUTPUT) {
            // The server can omit an echo of the correctly predicted empty output slot.
            // Clearing the inputs after collection, plus the reconciled result inventory, confirms that case.
            boolean outputCleared = idle(view, profile.outputSlot, profile.outputIdle)
                    || (view.localOutput() != null && view.localOutput().book() == null
                    && view.localOutput().idle().equals(profile.outputIdle)
                    && (view.slotRevisions().get(profile.firstSlot) > sentRevision
                    || view.slotRevisions().get(profile.secondSlot) > sentRevision));
            if (inputCount == beforeInputs - 2 && outputCount == beforeOutputs + 1 && idleInputs(view)
                    && outputCleared) {
                completed++;
                stage = Stage.READY;
                status = "Combined " + completed;
            }
            return -1;
        }
        if (now < nextClick || !view.localCursorEmpty()) return -1;
        if (stage == Stage.READY) {
            if (!idleInputs(view) || !idle(view, profile.outputSlot, profile.outputIdle)) {
                stop("Start with empty anvil inputs and no result"); return -1;
            }
            int first = -1, second = -1;
            for (activeLevel = profile.inputLevel; activeLevel < profile.maxLevel; activeLevel++) {
                first = find(view, -1);
                second = find(view, first);
                if (first >= 0 && second >= 0) break;
            }
            if (first < 0 || second < 0) {
                stop("Finished: no pair of selected, unstacked books"); return -1;
            }
            beforeInputs = count(view, activeLevel);
            beforeOutputs = count(view, activeLevel + 1);
            source = first;
            return send(first, Stage.FIRST, view, now, "Waiting for first input");
        }
        if (stage == Stage.FIRST) {
            if (!book(view, profile.firstSlot, activeLevel)
                    || !idle(view, profile.secondSlot, profile.secondIdle)
                    || view.slots().get(source).count() != 0 || inputCount != beforeInputs - 1) return -1;
            int second = find(view, -1);
            if (second < 0) { stop("Second book is no longer available"); return -1; }
            source = second;
            return send(second, Stage.SECOND, view, now, "Waiting for matching result");
        }
        if (stage == Stage.SECOND) {
            if (!book(view, profile.firstSlot, activeLevel)
                    || !book(view, profile.secondSlot, activeLevel)
                    || view.slots().get(source).count() != 0 || inputCount != beforeInputs - 2) return -1;
            Cell confirmation = view.slots().get(profile.confirmSlot);
            if (confirmation.book() != null || !confirmation.idle().startsWith(profile.confirmItem + "\n")) {
                stop("Anvil confirmation button changed"); return -1;
            }
            return send(profile.confirmSlot, Stage.CONFIRM, view, now, "Waiting for anvil confirmation");
        }
        if (stage == Stage.CONFIRM) {
            if (!book(view, profile.outputSlot, activeLevel + 1)
                    || view.slotRevisions().get(profile.outputSlot) <= sentRevision
                    || inputCount != beforeInputs - 2) return -1;
            // Servers may consume the inputs on confirmation, or when collecting the result.
            if ((!book(view, profile.firstSlot, activeLevel) && !idle(view, profile.firstSlot, profile.firstIdle))
                    || (!book(view, profile.secondSlot, activeLevel) && !idle(view, profile.secondSlot, profile.secondIdle))) return -1;
            boolean space = view.slots().subList(profile.rows * 9, view.slots().size()).stream().anyMatch(c -> c.count() == 0);
            if (!space) { stop("Inventory has no free slot for the result"); return -1; }
            return send(profile.outputSlot, Stage.OUTPUT, view, now, "Waiting for result in inventory");
        }
        return -1;
    }

    private int send(int slot, Stage next, Snapshot view, long now, String message) {
        stage = next;
        status = message;
        sentRevision = view.revision();
        deadline = now + RESPONSE_TIMEOUT_MS;
        nextClick = now + nextDelay();
        return slot;
    }

    /** Draw once per scheduled click, including the initial wait; endpoints are inclusive. */
    private int nextDelay() { return delays.nextInt(profile.minDelayMs, profile.maxDelayMs + 1); }

    private boolean idleInputs(Snapshot view) {
        return idle(view, profile.firstSlot, profile.firstIdle) && idle(view, profile.secondSlot, profile.secondIdle);
    }
    private boolean idle(Snapshot view, int slot, String marker) {
        Cell cell = view.slots().get(slot);
        return cell.book() == null && cell.idle().equals(marker);
    }
    private boolean book(Snapshot view, int slot, int level) {
        Cell cell = view.slots().get(slot);
        return cell.count() == 1 && new Book(profile.enchantment, level).equals(cell.book());
    }
    private int find(Snapshot view, int exclude) {
        for (int i = profile.rows * 9; i < view.slots().size(); i++)
            if (i != exclude && book(view, i, activeLevel)) return i;
        return -1;
    }
    private int count(Snapshot view, int level) {
        int count = 0;
        Book target = new Book(profile.enchantment, level);
        for (int i = profile.rows * 9; i < view.slots().size(); i++)
            if (target.equals(view.slots().get(i).book())) count += view.slots().get(i).count();
        return count;
    }

    public void stop(String reason) { running = false; status = reason; }
    public boolean running() { return running; }
    /** Applies to the most recently returned click slot: the anvil button uses a normal click. */
    public boolean shiftClick() { return stage != Stage.CONFIRM; }
    public String status() { return status; }
    public int completed() { return completed; }
}
