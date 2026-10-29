package dev.profittracker;

/** Last authoritative reading plus observed kills since then. A fresh reading may correct downwards. */
public final class BestiaryTotal {
    public long confirmed = -1;
    public long extra;
    public long updatedAt;
    public long sourceSavedAt;
    public String profileName = "";
    public void sanitize() {
        if (confirmed < -1 || confirmed > 1_000_000_000_000L) confirmed = -1;
        extra = Math.clamp(extra, 0, 1_000_000_000_000L);
        updatedAt = Math.max(0, updatedAt);
        if (profileName == null || profileName.length() > 40) profileName = "";
    }
    public boolean confirm(long kills, long now) {
        if (kills < 0 || kills > 1_000_000_000_000L) return false;
        confirmed = kills; extra = 0; updatedAt = now; return true;
    }
    public boolean update(BestiaryApiData data, long now) {
        if (data.savedAt() > 0 && data.savedAt() < sourceSavedAt) return false;
        if (confirmed == data.kills() && (data.savedAt() == 0 || data.savedAt() == sourceSavedAt)) return false;
        if (!confirm(data.kills(), now)) return false;
        sourceSavedAt = data.savedAt(); profileName = data.profileName(); return true;
    }
    public void kill() { if (confirmed >= 0 && extra < 1_000_000_000_000L) extra++; }
    public long total() { return confirmed < 0 ? -1 : confirmed + extra; }
}
