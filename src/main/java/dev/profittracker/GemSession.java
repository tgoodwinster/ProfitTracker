package dev.profittracker;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Message-derived gains, with independent lifetime totals and a resettable hourly-rate window. */
public final class GemSession {
    private static final long MINING_GRACE_MS = 2_000;
    private static final long COUNT_LIMIT = 1_000_000_000_000L;
    private final EnumMap<Gemstone, Long> flawed = new EnumMap<>(Gemstone.class);
    private final EnumMap<Gemstone, Long> rateBaseline = new EnumMap<>(Gemstone.class);
    private boolean running;
    private boolean eligible;
    private boolean active;
    private long lastTick = -1;
    private long lastActivity = Long.MIN_VALUE / 2;
    private long glossySafeAfter;
    private long activeMillis;
    private long rateMillis;
    private long procs;
    private long glossy;
    private long glossyBaseline;

    public void start(long now) { running = true; interrupt(now); }
    public void pause(long now) { running = false; interrupt(now); }
    public void interrupt(long now) {
        eligible = active = false;
        lastTick = now;
        lastActivity = Long.MIN_VALUE / 2;
        glossySafeAfter = now + 5_000;
    }

    public void tick(long now, boolean allowed, boolean mining, boolean miningTimeOnly) {
        if (running && allowed && mining) lastActivity = now;
        boolean nowActive = running && allowed && (!miningTimeOnly || recentlyMining(now));
        if (lastTick >= 0 && eligible && allowed && active && nowActive) {
            long delta = Math.clamp(now - lastTick, 0, 1_000);
            activeMillis += delta;
            rateMillis += delta;
        }
        lastTick = now;
        eligible = allowed;
        active = nowActive;
    }

    public boolean pristine(GemMessages.Pristine gain, long now) {
        if (!running || gain.flawed() <= 0 || gain.flawed() > 1_000_000) return false;
        long previous = flawed.getOrDefault(gain.gemstone(), 0L);
        if (previous > COUNT_LIMIT - gain.flawed()) return false;
        flawed.put(gain.gemstone(), previous + gain.flawed());
        procs++;
        lastActivity = now;
        return true;
    }

    public boolean glossy(long amount, long now) {
        if (!running || !eligible || !recentlyMining(now) || now < glossySafeAfter
                || amount <= 0 || amount > 1_000_000 || glossy > COUNT_LIMIT - amount) return false;
        glossy += amount;
        return true;
    }

    /** Reset the numerator as well as time so old earnings cannot inflate a fresh hourly rate. */
    public void resetRate(long now) {
        rateBaseline.clear();
        rateBaseline.putAll(flawed);
        glossyBaseline = glossy;
        rateMillis = 0;
        lastTick = now;
    }

    public OptionalDouble value(BazaarPrices prices, boolean includeGlossy, boolean rateWindow) {
        if (prices == null) return OptionalDouble.empty();
        double coins = 0;
        for (var entry : flawed.entrySet()) {
            long count = entry.getValue() - (rateWindow ? rateBaseline.getOrDefault(entry.getKey(), 0L) : 0);
            if (count == 0) continue;
            double price = prices.flawless().getOrDefault(entry.getKey(), Double.NaN);
            if (!Double.isFinite(price) || price <= 0) return OptionalDouble.empty();
            coins += count / (double) Gemstone.FLAWED_PER_FLAWLESS * price;
        }
        long glossyCount = glossy - (rateWindow ? glossyBaseline : 0);
        if (includeGlossy && glossyCount > 0) {
            if (!Double.isFinite(prices.glossy()) || prices.glossy() <= 0) return OptionalDouble.empty();
            coins += glossyCount * prices.glossy();
        }
        return OptionalDouble.of(coins);
    }

    public boolean running() { return running; }
    public boolean active() { return active; }
    public boolean recentlyMining(long now) { return now - lastActivity <= MINING_GRACE_MS; }
    public long activeMillis() { return activeMillis; }
    public long rateMillis() { return rateMillis; }
    public long procs() { return procs; }
    public long glossy() { return glossy; }
    public long flawedTotal() { return flawed.values().stream().mapToLong(Long::longValue).sum(); }
    public Map<Gemstone, Long> flawed() { return Map.copyOf(flawed); }
}
