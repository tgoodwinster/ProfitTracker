package dev.profittracker;

/** Five separated pulses, with no catch-up sound spam after a stalled frame. */
public final class AlertBurst {
    private boolean active;
    private int sent;
    private long next, end;
    public void start(long now) { active = true; sent = 0; next = now; end = now + 5_000; }
    public boolean tick(long now) {
        if (!active) return false;
        if (sent == 5) { if (now >= end) active = false; return false; }
        if (now < next) return false;
        sent++; next = now + 1_000; end = Math.max(end, next); return true;
    }
    public boolean active() { return active; }
    public boolean finished() { return sent == 5 && !active; }
    public void clear() { active = false; sent = 0; }
}
