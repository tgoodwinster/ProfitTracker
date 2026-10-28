package dev.profittracker;

import java.util.Locale;

public final class CompactNumbers {
    private CompactNumbers() { }
    public static String format(double value) {
        if (!Double.isFinite(value)) return "--";
        String[] suffixes = {"", "k", "m", "b", "t"};
        int index = 0;
        double number = Math.abs(value);
        while (number >= 1_000 && index < suffixes.length - 1) { number /= 1_000; index++; }
        number = Math.floor(number * (index == 0 ? 1 : 100)) / (index == 0 ? 1 : 100);
        String text = index == 0 ? String.format(Locale.ROOT, "%.0f", number)
                : String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
        return (value < 0 && number != 0 ? "-" : "") + text + suffixes[index];
    }
    public static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1_000;
        return seconds >= 3_600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
