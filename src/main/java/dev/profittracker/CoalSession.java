package dev.profittracker;

import java.util.ArrayDeque;
import java.util.Comparator;

/** Single client-thread ledger. No inventory, sack, and compactor totals are added independently. */
public final class CoalSession {
    public static final long SETTLE_MS = 6_000;
    public static final long QUARANTINE_MS = 10_000;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private boolean running;
    private boolean eligible;
    private long lastTick = -1;
    private long elapsed;
    private long safeAfter;
    private long lastBreak = Long.MIN_VALUE / 2;
    private CoalStock baseline = CoalStock.ZERO;
    private CoalStock inventory = CoalStock.ZERO;
    private CoalStock sacks = CoalStock.ZERO;
    private CoalStock committedShares = CoalStock.ZERO;
    private CoalStock segmentShares = CoalStock.ZERO;
    private long committedUnits;
    private long segmentUnits;

    public boolean running() { return running; }
    public long coal() { return committedUnits + segmentUnits; }
    public long elapsedMillis() { return elapsed; }
    public CoalStock shares() { return committedShares.plus(segmentShares); }
    public boolean settling(long now) { return now < safeAfter; }

    public void start(long now, CoalStock stock) {
        if (!running) {
            running = true;
            interrupt(now, stock);
            lastTick = now;
        }
    }

    public void pause(long now, CoalStock stock) {
        interrupt(now, stock);
        running = false;
        eligible = false;
        lastTick = now;
    }

    public void reset(long now, CoalStock stock) {
        committedUnits = segmentUnits = elapsed = 0;
        committedShares = segmentShares = CoalStock.ZERO;
        samples.clear();
        interrupt(now, stock);
        lastTick = now;
    }

    /** Freeze confirmed gains; discard the ambiguous tail and establish a fresh baseline. */
    public void interrupt(long now, CoalStock stock) {
        committedUnits += segmentUnits;
        committedShares = committedShares.plus(segmentShares);
        segmentUnits = 0;
        segmentShares = CoalStock.ZERO;
        inventory = baseline = stock;
        sacks = CoalStock.ZERO;
        samples.clear();
        lastBreak = Long.MIN_VALUE / 2;
        safeAfter = now + QUARANTINE_MS;
    }

    public void tick(long now, CoalStock stock, boolean allowed, boolean coalBroken) {
        if (lastTick >= 0 && running && eligible && allowed) {
            // A suspended client/disconnect cannot silently add hours to the session.
            elapsed += Math.max(0, Math.min(now - lastTick, 1_000));
        }
        lastTick = now;
        eligible = allowed;
        if (!running || !allowed) {
            interrupt(now, stock);
            return;
        }
        if (now < safeAfter) {
            inventory = baseline = stock;
            return;
        }
        if (coalBroken) lastBreak = now;
        if (!stock.equals(inventory) && now - lastBreak > QUARANTINE_MS) {
            interrupt(now, stock);
            return;
        }
        inventory = stock;
        CoalStock net = inventory.minus(baseline).plus(sacks);
        if (samples.isEmpty()) samples.addLast(new Sample(now, CoalStock.ZERO));
        samples.addLast(new Sample(now, net));
        while (!samples.isEmpty() && samples.getFirst().time < now - SETTLE_MS) {
            samples.removeFirst();
        }
        // A gain must survive the reconciliation window, not just an intermediate slot packet.
        Sample minimum = samples.stream().min(Comparator.comparingLong(s -> s.stock.units())).orElseThrow();
        segmentUnits = Math.max(0, minimum.stock.units());
        segmentShares = minimum.stock.earnedShare(segmentUnits);
    }

    public void sackChange(long now, CoalStock stock, CoalStock delta, boolean complete) {
        if (!running || !eligible || now < safeAfter) return;
        // Withdrawals / sack-to-sack conversions cannot be reliably attributed to mining.
        if (!complete || delta.coal() < 0 || delta.enchanted() < 0 || delta.enchantedBlocks() < 0) {
            interrupt(now, stock);
        } else if (delta.units() > 0) {
            if (now - lastBreak > QUARANTINE_MS) interrupt(now, stock);
            else sacks = sacks.plus(delta);
        }
    }

    private record Sample(long time, CoalStock stock) { }
}
