package dev.profittracker;

import java.util.Locale;

/** Shared by the Endermite alert and each custom chat title. */
public final class AlertStyle {
    public String text = "KILL";
    public String color = "55FF55";
    public double scale = 4;
    public boolean bold = true;
    public double seconds = 3;
    public boolean soundEnabled = true;
    public Sound sound = Sound.DING;
    public double volume = 0.8;
    public TrackerSettings.HudPosition position = new TrackerSettings.HudPosition(0.5, 0.42);

    public enum Sound { DING, BELL, LEVEL_UP }

    public void sanitize() {
        text = ChatTitleRule.clean(text == null ? "KILL" : text);
        if (text.isEmpty()) text = "KILL";
        if (text.length() > 96) text = text.substring(0, 96);
        color = normalizeColor(color);
        scale = Double.isFinite(scale) ? Math.clamp(scale, 0.5, 8) : 4;
        seconds = Double.isFinite(seconds) ? Math.clamp(seconds, 0.5, 15) : 3;
        volume = Double.isFinite(volume) ? Math.clamp(volume, 0, 1) : 0.8;
        if (sound == null) sound = Sound.DING;
        if (position == null) position = new TrackerSettings.HudPosition(0.5, 0.42);
        position.sanitize();
    }

    public static String normalizeColor(String value) {
        String hex = value == null ? "" : value.replace("#", "").trim();
        return hex.matches("[0-9a-fA-F]{6}") ? hex.toUpperCase(Locale.ROOT) : "55FF55";
    }

    public int argb() { return 0xFF000000 | Integer.parseInt(normalizeColor(color), 16); }

    public AlertStyle copy() {
        var copy = new AlertStyle();
        copy.text = text;
        copy.color = color;
        copy.scale = scale;
        copy.bold = bold;
        copy.seconds = seconds;
        copy.soundEnabled = soundEnabled;
        copy.sound = sound;
        copy.volume = volume;
        copy.position = position == null ? null : new TrackerSettings.HudPosition(position.x, position.y);
        copy.sanitize();
        return copy;
    }
}
