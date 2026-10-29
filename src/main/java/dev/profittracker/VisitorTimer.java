package dev.profittracker;

/** Epoch-based progress survives game restarts. State mutates only on user/context transitions. */
public final class VisitorTimer {
    private final VisitorSettings state;
    public VisitorTimer(VisitorSettings state) { this.state = state; state.sanitize(); }
    public int count(long now) {
        if (!state.running || state.intervalSeconds <= 0) return state.baseCount;
        long elapsed = Math.max(0, now - state.anchorMillis);
        return (int) Math.min(state.capacity, state.baseCount + elapsed / (state.intervalSeconds * 1_000L));
    }
    public long remaining(long now) {
        if (state.intervalSeconds == 0 || count(now) >= state.capacity) return 0;
        long total = (state.capacity - state.baseCount) * state.intervalSeconds * 1_000L;
        long progress = state.running ? Math.max(0, now - state.anchorMillis) : state.partialMillis;
        return Math.max(0, total - progress);
    }
    public boolean pause(long now) {
        if (!state.running) return false;
        int current = count(now);
        state.partialMillis = current >= state.capacity ? 0 : Math.max(0, now - state.anchorMillis) % (state.intervalSeconds * 1_000L);
        state.baseCount = current; state.running = false; state.anchorMillis = 0; return true;
    }
    public boolean resume(long now) {
        if (state.running || !state.enabled || state.intervalSeconds == 0 || state.baseCount >= state.capacity) return false;
        state.running = state.armed = true; state.anchorMillis = Math.max(1, now - state.partialMillis); return true;
    }
    public void correct(int count, long now) {
        state.baseCount = Math.clamp(count, 0, state.capacity); state.partialMillis = 0;
        state.anchorMillis = Math.max(1, now); state.notified = false;
        state.running = state.enabled && state.intervalSeconds > 0 && count < state.capacity;
        state.armed = true;
    }
    public void reconfigure(int capacity, int seconds, long now) {
        int current = count(now); boolean wasRunning = state.running, wasArmed = state.armed;
        state.capacity = Math.clamp(capacity, 1, 30); state.intervalSeconds = Math.clamp(seconds, 0, 7_200);
        correct(current, now); state.armed = wasArmed;
        state.running = wasRunning && state.intervalSeconds > 0 && state.baseCount < state.capacity;
    }
    public boolean ready(long now) { return state.enabled && state.armed && state.intervalSeconds > 0 && count(now) >= state.capacity && !state.notified; }
    public String line(long now) {
        if (state.intervalSeconds == 0) return "Visitors: set interval";
        String status = count(now) >= state.capacity ? "Ready" : !state.running ? "Paused" : CompactNumbers.shortTime(remaining(now) + 59_999);
        return "Visitors ~" + count(now) + "/" + state.capacity + " | " + status;
    }
}
