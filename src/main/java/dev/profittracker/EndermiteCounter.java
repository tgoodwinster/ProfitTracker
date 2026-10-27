package dev.profittracker;

import java.util.HashSet;
import java.util.UUID;

/** A snapshot of nearby living mobs, never an unobservable personal spawn total. */
public final class EndermiteCounter {
    private int count;
    private int target;
    private boolean atTarget;
    private boolean armed = true;
    private long belowSince = -1;

    public int count() { return count; }
    public boolean atTarget() { return atTarget; }

    /** Return true once per threshold crossing; require a second below it before rearming. */
    public boolean update(Iterable<UUID> aliveNearby, int requestedTarget, long now) {
        var unique = new HashSet<UUID>();
        aliveNearby.forEach(unique::add);
        count = unique.size();
        int newTarget = Math.clamp(requestedTarget, 0, 200);
        if (target != newTarget) { armed = true; belowSince = -1; }
        target = newTarget;
        atTarget = target > 0 && count >= target;
        if (!atTarget) {
            if (belowSince < 0) belowSince = now;
            if (now - belowSince >= 1_000) armed = true;
            return false;
        }
        belowSince = -1;
        if (!armed) return false;
        armed = false;
        return true;
    }

    public void clear() {
        count = target = 0;
        atTarget = false;
        armed = true;
        belowSince = -1;
    }
}
