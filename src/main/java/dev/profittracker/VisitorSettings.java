package dev.profittracker;

/** A manually configured estimate. No claim is made about actual server visitor stock. */
public final class VisitorSettings {
    public boolean enabled;
    public boolean hudEnabled = true;
    public boolean autoGarden = true;
    public boolean alertEnabled = true;
    public int capacity = 10;
    public int intervalSeconds;
    public int baseCount;
    public long anchorMillis;
    public long partialMillis;
    public boolean running;
    public boolean armed;
    public boolean notified;
    public String profile = "";
    public AlertStyle alert = defaults();
    private static AlertStyle defaults() {
        var style = new AlertStyle(); style.text = "OFFLINE VISITORS READY"; style.scale = 4;
        style.sound = AlertStyle.Sound.BELL; style.volume = 1; style.seconds = 0.65; return style;
    }
    public void sanitize() {
        capacity = Math.clamp(capacity, 1, 30);
        intervalSeconds = Math.clamp(intervalSeconds, 0, 7_200);
        baseCount = Math.clamp(baseCount, 0, capacity);
        anchorMillis = Math.max(0, anchorMillis);
        partialMillis = Math.clamp(partialMillis, 0, Math.max(0, intervalSeconds * 1_000L - 1));
        if (intervalSeconds == 0 || anchorMillis == 0 && running) running = false;
        if (profile == null || profile.length() > 120) profile = "";
        if (alert == null) alert = defaults();
        alert.sanitize();
    }
}
