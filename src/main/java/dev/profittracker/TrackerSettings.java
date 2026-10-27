package dev.profittracker;

/** Small, local-only configuration. A target of zero disables threshold alerts. */
public final class TrackerSettings {
    public boolean coalHudEnabled = true;
    public boolean endermitesEnabled = true;
    public boolean endermiteTitle = true;
    public int endermiteTarget = 25;
    public HudPosition coalPosition = new HudPosition(0.015, 0.08);
    public HudPosition endermitePosition = new HudPosition(0.015, 0.38);

    public void sanitize() {
        endermiteTarget = Math.clamp(endermiteTarget, 0, 200);
        if (coalPosition == null) coalPosition = new HudPosition(0.015, 0.08);
        if (endermitePosition == null) endermitePosition = new HudPosition(0.015, 0.38);
        coalPosition.sanitize();
        endermitePosition.sanitize();
    }

    public void resetPositions() {
        coalPosition = new HudPosition(0.015, 0.08);
        endermitePosition = new HudPosition(0.015, 0.38);
    }

    /** Fractions of the available space keep the entire panel visible after a GUI scale change. */
    public static final class HudPosition {
        public double x;
        public double y;

        public HudPosition(double x, double y) { this.x = x; this.y = y; }

        private static double fraction(double value) {
            return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
        }

        public void sanitize() { x = fraction(x); y = fraction(y); }
        public int left(int screenWidth, int panelWidth) {
            return (int) Math.round(fraction(x) * Math.max(0, screenWidth - panelWidth));
        }
        public int top(int screenHeight, int panelHeight) {
            return (int) Math.round(fraction(y) * Math.max(0, screenHeight - panelHeight));
        }
        public void moveTo(double left, double top, int screenWidth, int screenHeight, int panelWidth, int panelHeight) {
            x = fraction(left / Math.max(1, screenWidth - panelWidth));
            y = fraction(top / Math.max(1, screenHeight - panelHeight));
        }
    }
}
