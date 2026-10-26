package dev.profittracker;

import java.util.List;
import java.util.regex.Pattern;

/** Parse item-level hover text, never the aggregate '[Sacks] +N items' chat total. */
public final class SackParser {
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE = Pattern.compile(
            "^\\s*([+-][\\d,]+) (Coal|Block of Coal|Enchanted Coal|Enchanted Coal Block|Enchanted Block of Coal) \\(([^)]+)\\)\\s*$");

    private SackParser() { }

    public static String clean(String text) { return FORMATTING.matcher(text).replaceAll(""); }

    public static Result parse(String message, List<String> hoverTexts) {
        if (!clean(message).startsWith("[Sacks]")) return new Result(CoalStock.ZERO, true);
        CoalStock result = CoalStock.ZERO;
        boolean complete = true;
        boolean sawSection = false;
        // The adapter supplies each distinct hover component once (not repeated inherited styles).
        for (String hover : hoverTexts) {
            String text = clean(hover);
            if (!text.startsWith("Added") && !text.startsWith("Removed")) continue;
            sawSection = true;
            if (text.toLowerCase(java.util.Locale.ROOT).contains("other items")) complete = false;
            for (String line : text.split("\\R")) {
                var match = LINE.matcher(line);
                if (!match.matches()) {
                    if (line.contains("Coal")) complete = false;
                    continue;
                }
                if (!match.group(3).contains("Mining Sack")) continue;
                try {
                    long count = Long.parseLong(match.group(1).replace(",", ""));
                    // Reject malformed / unreasonable server data, do not overflow a ledger.
                    if (Math.abs(count) > 1_000_000_000L) { complete = false; continue; }
                    result = result.plus(switch (match.group(2)) {
                        case "Coal" -> new CoalStock(count, 0, 0);
                        case "Block of Coal" -> new CoalStock(count * 9, 0, 0);
                        case "Enchanted Coal" -> new CoalStock(0, count, 0);
                        case "Enchanted Coal Block", "Enchanted Block of Coal" -> new CoalStock(0, 0, count);
                        default -> CoalStock.ZERO;
                    });
                } catch (NumberFormatException ex) { complete = false; }
            }
        }
        // Missing hover data is unobservable, not an item gain of zero that we can safely merge.
        if (!sawSection) complete = false;
        return new Result(result, complete);
    }

    public record Result(CoalStock delta, boolean complete) { }
}
