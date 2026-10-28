package dev.profittracker;

/** A trigger is latched for its duration, even when the original condition immediately stops. */
public final class TitleTimeline {
    private AlertStyle active;
    private String source;
    private long until;

    public void show(AlertStyle style, String source, long now) {
        active = style.copy();
        this.source = source;
        until = now + Math.round(active.seconds * 1000);
    }

    public AlertStyle current(long now) {
        if (now >= until) clear();
        return active;
    }

    public void clearSource(String value) { if (value.equals(source)) clear(); }
    public void clear() { active = null; source = null; until = 0; }
}
