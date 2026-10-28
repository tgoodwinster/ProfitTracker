package dev.profittracker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Small, local-only configuration. A target of zero disables threshold alerts. */
public final class TrackerSettings {
    public boolean coalHudEnabled = true;
    public boolean endermitesEnabled = true;
    public boolean endermiteTitle = true;
    public int endermiteTarget = 25;
    public int endermiteRange = 48;
    public AlertStyle endermiteAlert = new AlertStyle();
    public boolean customTitlesEnabled = true;
    public List<ChatTitleRule> customTitles = new ArrayList<>();
    public BookCombinerSettings books = new BookCombinerSettings();
    public HudPosition coalPosition = new HudPosition(0.015, 0.08);
    public HudPosition endermitePosition = new HudPosition(0.015, 0.38);

    public void sanitize() {
        if (books == null) books = new BookCombinerSettings();
        books.sanitize();
        endermiteTarget = Math.clamp(endermiteTarget, 0, 200);
        endermiteRange = Math.clamp(endermiteRange, 4, 128);
        if (endermiteAlert == null) endermiteAlert = new AlertStyle();
        endermiteAlert.sanitize();
        if (customTitles == null) customTitles = new ArrayList<>();
        customTitles = new ArrayList<>(customTitles.stream().filter(java.util.Objects::nonNull).limit(50).toList());
        var ids = new HashSet<String>();
        for (var rule : customTitles) {
            rule.sanitize();
            if (!ids.add(rule.id)) { rule.id = UUID.randomUUID().toString(); ids.add(rule.id); }
        }
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
