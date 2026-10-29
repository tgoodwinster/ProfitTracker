package dev.profittracker;

import java.util.HashMap;
import java.util.UUID;

/** Count server-confirmed deaths linked to recent personal hits; never infer kills from disappearances. */
public final class EndermiteSession {
    private final HashMap<UUID, Long> hits = new HashMap<>();
    private final HashMap<UUID, Long> deaths = new HashMap<>();
    private long kills;
    private long elapsed;
    private long lastTick = -1;
    private boolean allowed;
    private boolean personalDeath;

    public void tick(long now, boolean enabledHere) {
        if (allowed && enabledHere && lastTick >= 0) elapsed += Math.max(0, now - lastTick);
        allowed = enabledHere;
        lastTick = now;
        hits.entrySet().removeIf(e -> now - e.getValue() > 5_000);
        deaths.entrySet().removeIf(e -> now - e.getValue() > 60_000);
    }
    public void hit(UUID id, long now) { if (allowed && hits.size() < 512) hits.put(id, now); }
    public void damage(UUID id, boolean yours, long now) {
        if (yours) hit(id, now); else hits.remove(id);
    }
    public boolean death(UUID id, long now, boolean nearbyDeaths) {
        personalDeath = false;
        if (!allowed || deaths.containsKey(id)) return false;
        if (deaths.size() >= 2_048) return false;
        deaths.put(id, now);
        Long hit = hits.remove(id);
        personalDeath = hit != null && now >= hit && now - hit <= 5_000;
        if (!nearbyDeaths && !personalDeath) return false;
        kills++;
        return true;
    }
    public void clearEvidence() { hits.clear(); deaths.clear(); allowed = false; lastTick = -1; }
    public void reset(long now) { kills = elapsed = 0; hits.clear(); deaths.clear(); lastTick = now; }
    public long kills() { return kills; }
    public boolean personalDeath() { return personalDeath; }
    public long elapsedMillis() { return elapsed; }
    public String rate() { return elapsed < 60_000 ? "--" : CompactNumbers.format(kills * 3_600_000.0 / elapsed); }
}
