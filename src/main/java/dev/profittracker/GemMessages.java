package dev.profittracker;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Exact gain messages only. Never interpret player chat, aggregate sacks, or inventory changes. */
public final class GemMessages {
    public static final String DEFAULT_GLOSSY_FORMAT = "[SkyOcean] Sack Notification | Glossy Gemstone x{amount}";
    static final String LEGACY_GLOSSY_FORMAT = "Sack Notification: Glossy Gemstone x{amount}";
    private static final String COUNT = "(?:[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)";
    private static final Pattern PRISTINE = Pattern.compile(
            "^PRISTINE! You found (?:an? )?[^\\p{L}\\p{N}]{0,8}Flawed ([A-Za-z]+) Gemstone (?:x(" + COUNT + ")|\\(x(" + COUNT + ")\\))!$");
    private static final Pattern NUMBER = Pattern.compile("^" + COUNT + "$");
    private static final Pattern EXAMPLE_AMOUNT = Pattern.compile("[x×]\\s*(" + COUNT + ")(?=[!.)\\s]*$)");
    public record Pristine(Gemstone gemstone, long flawed) { }

    private GemMessages() { }

    public static Optional<Pristine> pristine(String raw) {
        if (raw == null || raw.length() > 2_048) return Optional.empty();
        String clean = ChatTitleRule.clean(raw);
        var match = PRISTINE.matcher(clean);
        if (!match.matches()) return Optional.empty();
        long amount = amount(match.group(2) != null ? match.group(2) : match.group(3));
        if (amount <= 0) return Optional.empty();
        return Gemstone.named(match.group(1)).map(gemstone -> new Pristine(gemstone, amount));
    }

    public static boolean validGlossyFormat(String raw) {
        String format = ChatTitleRule.clean(raw).toLowerCase(Locale.ROOT);
        int token = format.indexOf("{amount}");
        return format.length() <= 512 && token >= 0 && token == format.lastIndexOf("{amount}")
                && format.contains("glossy gemstone")
                && !format.contains("removed") && !format.contains("withdraw")
                && !format.substring(0, token).stripTrailing().endsWith("-");
    }

    public static OptionalLong glossy(String raw, String template) {
        if (raw == null || raw.length() > 2_048 || !validGlossyFormat(template)) return OptionalLong.empty();
        String message = ChatTitleRule.clean(raw).toLowerCase(Locale.ROOT);
        String format = ChatTitleRule.clean(template).toLowerCase(Locale.ROOT);
        int token = format.indexOf("{amount}");
        String before = format.substring(0, token), after = format.substring(token + 8);
        if (!message.startsWith(before) || !message.endsWith(after) || message.length() <= before.length() + after.length()) return OptionalLong.empty();
        long count = amount(message.substring(before.length(), message.length() - after.length()));
        return count > 0 ? OptionalLong.of(count) : OptionalLong.empty();
    }

    public static String formatFromExample(String raw) {
        String text = ChatTitleRule.clean(raw);
        if (!text.toLowerCase(Locale.ROOT).contains("glossy gemstone")) return text;
        var match = EXAMPLE_AMOUNT.matcher(text);
        if (!match.find()) return text;
        return text.substring(0, match.start(1)) + "{amount}" + text.substring(match.end(1));
    }

    private static long amount(String value) {
        if (value.length() > 12 || !NUMBER.matcher(value).matches()) return 0;
        try {
            long count = Long.parseLong(value.replace(",", ""));
            return count <= 1_000_000 ? count : 0;
        } catch (NumberFormatException ex) { return 0; }
    }
}
