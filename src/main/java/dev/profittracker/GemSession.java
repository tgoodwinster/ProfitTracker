package dev.profittracker;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Observed gains, lifetime totals, and a bounded rolling window measured in running-session time. */
public final class GemSession {
    private static final long MINING_GRACE_MS = 2_000;
    private static final long SACK_GRACE_MS = 10_000;
    private static final long MAX_WINDOW_MS = 600_000;
    private static final long COUNT_LIMIT = 1_000_000_000_000L;
    private final EnumMap<Gemstone, Long> flawed = new EnumMap<>(Gemstone.class);
    private final EnumMap<Gemstone, Long> rough = new EnumMap<>(Gemstone.class);
    private final ArrayDeque<Bucket> recent = new ArrayDeque<>();
    private boolean running;
    private boolean eligible;
    private boolean active;
    private long lastTick = -1;
    private long lastActivity = Long.MIN_VALUE / 2;
    private long sackSafeAfter;
    private long activeMillis;
    private long rateMillis;
    private long procs;
    private long glossy;

    private static final class Bucket {
        final long time;
        final EnumMap<Gemstone, Long> units = new EnumMap<>(Gemstone.class);
        long glossy;
        Bucket(long time) { this.time = time; }
    }

    public void start(long now) { running = true; interrupt(now); }
    public void pause(long now) { running = false; interrupt(now); }
    public void interrupt(long now) {
        eligible = active = false;
        lastTick = now;
        quarantineSacks(now);
    }

    /** Inventory transitions invalidate delayed sack provenance without pausing running-session time. */
    public void quarantineSacks(long now) {
        lastActivity = Long.MIN_VALUE / 2;
        sackSafeAfter = now + 5_000;
    }

    public void tick(long now, boolean allowed, boolean mining, boolean miningTimeOnly) {
        if (running && allowed && mining) lastActivity = now;
        boolean nowActive = running && allowed && (!miningTimeOnly || recentlyMining(now));
        if (lastTick >= 0 && eligible && allowed && active && nowActive) {
            long delta = Math.max(0, now - lastTick);
            if (miningTimeOnly) delta = Math.min(delta, 1_000);
            activeMillis += delta;
            rateMillis += delta;
        }
        lastTick = now;
        eligible = allowed;
        active = nowActive;
        prune();
    }

    public boolean pristine(GemMessages.Pristine gain, long now) {
        if (!running || gain.flawed() <= 0 || gain.flawed() > 1_000_000) return false;
        long previous = flawed.getOrDefault(gain.gemstone(), 0L);
        if (previous > COUNT_LIMIT - gain.flawed()) return false;
        flawed.put(gain.gemstone(), previous + gain.flawed());
        bucket().units.merge(gain.gemstone(), gain.flawed() * Gemstone.ROUGH_PER_FLAWED, Long::sum);
        procs++;
        lastActivity = now;
        return true;
    }

    public boolean rough(Map<Gemstone, Long> gains, long now) {
        if (!sackAllowed(now) || gains.isEmpty()) return false;
        for (var entry : gains.entrySet()) {
            long amount = entry.getValue();
            if (amount <= 0 || amount > 1_000_000 || rough.getOrDefault(entry.getKey(), 0L) > COUNT_LIMIT - amount) return false;
        }
        for (var entry : gains.entrySet()) {
            rough.merge(entry.getKey(), entry.getValue(), Long::sum);
            bucket().units.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        return true;
    }

    public boolean glossy(long amount, long now) {
        if (!sackAllowed(now) || amount <= 0 || amount > 1_000_000 || glossy > COUNT_LIMIT - amount) return false;
        glossy += amount;
        bucket().glossy += amount;
        return true;
    }

    private boolean sackAllowed(long now) {
        return running && eligible && recentlyMining(now, SACK_GRACE_MS) && now >= sackSafeAfter;
    }

    private Bucket bucket() {
        var last = recent.peekLast();
        if (last == null || last.time / 1_000 != activeMillis / 1_000) {
            last = new Bucket(activeMillis);
            recent.addLast(last);
        }
        prune();
        return last;
    }

    private void prune() {
        while (!recent.isEmpty() && (recent.peekFirst().time < activeMillis - MAX_WINDOW_MS || recent.size() > 601)) recent.removeFirst();
    }

    /** Keep totals, clear recent earnings, and warm up a fresh rate window. */
    public void resetRate(long now) {
        recent.clear();
        rateMillis = 0;
        lastTick = now;
    }

    public OptionalDouble value(GemPrices prices, boolean includeGlossy) {
        var units = new EnumMap<Gemstone, Long>(Gemstone.class);
        flawed.forEach((gem, count) -> units.put(gem, count * Gemstone.ROUGH_PER_FLAWED));
        rough.forEach((gem, count) -> units.merge(gem, count, Long::sum));
        return valueOf(prices, units, includeGlossy ? glossy : 0);
    }

    public OptionalDouble recentValue(GemPrices prices, boolean includeGlossy, long windowMillis) {
        var units = new EnumMap<Gemstone, Long>(Gemstone.class);
        long glossyCount = 0;
        long cutoff = activeMillis - Math.clamp(windowMillis, 1, MAX_WINDOW_MS);
        for (var bucket : recent) {
            if (bucket.time < cutoff) continue;
            bucket.units.forEach((gem, count) -> units.merge(gem, count, Long::sum));
            if (includeGlossy) glossyCount += bucket.glossy;
        }
        return valueOf(prices, units, glossyCount);
    }

    public OptionalDouble coinsPerHour(GemPrices prices, boolean includeGlossy, int averageMinutes) {
        long window = Math.clamp(averageMinutes, 1, 10) * 60_000L;
        if (rateMillis < window) return OptionalDouble.empty();
        var value = recentValue(prices, includeGlossy, window);
        return value.isPresent() ? OptionalDouble.of(value.getAsDouble() * 3_600_000 / window) : OptionalDouble.empty();
    }

    private static OptionalDouble valueOf(GemPrices prices, Map<Gemstone, Long> units, long glossy) {
        if (prices == null) return OptionalDouble.empty();
        double coins = 0;
        for (var entry : units.entrySet()) {
            var sale = prices.best().get(entry.getKey());
            if (sale == null || !Double.isFinite(sale.perFlawed()) || sale.perFlawed() <= 0) return OptionalDouble.empty();
            coins += entry.getValue() / (double) Gemstone.ROUGH_PER_FLAWED * sale.perFlawed();
        }
        if (glossy > 0) {
            if (!Double.isFinite(prices.glossy()) || prices.glossy() <= 0) return OptionalDouble.empty();
            coins += glossy * prices.glossy();
        }
        return OptionalDouble.of(coins);
    }

    public boolean running() { return running; }
    public boolean active() { return active; }
    public boolean recentlyMining(long now) { return recentlyMining(now, MINING_GRACE_MS); }
    private boolean recentlyMining(long now, long grace) {
        long elapsed = now - lastActivity;
        return elapsed >= 0 && elapsed <= grace;
    }
    public long activeMillis() { return activeMillis; }
    public long rateMillis() { return rateMillis; }
    public long procs() { return procs; }
    public long glossy() { return glossy; }
    public long flawedTotal() { return flawed.values().stream().mapToLong(Long::longValue).sum(); }
    public long roughTotal() { return rough.values().stream().mapToLong(Long::longValue).sum(); }
    public Map<Gemstone, Long> flawed() { return Map.copyOf(flawed); }
    public Map<Gemstone, Long> rough() { return Map.copyOf(rough); }
    int bucketCount() { return recent.size(); }
}
