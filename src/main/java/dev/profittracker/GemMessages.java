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
    private static final Pattern EXAMPLE_AMOUNT = Pattern.compile("glossy gemstone [x×]\\s*(" + COUNT + ")(?=[!.)\\s]*(?:$|, ))", Pattern.CASE_INSENSITIVE);
    private static final String SKYOCEAN_PREFIX = "[skyocean] sack notification | ";
    private static final Pattern SKYOCEAN_ITEM = Pattern.compile("^(.+?) x(" + COUNT + ")$");
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
        if (format.equals(DEFAULT_GLOSSY_FORMAT.toLowerCase(Locale.ROOT))) return skyOceanGlossy(message);
        int token = format.indexOf("{amount}");
        String before = format.substring(0, token), after = format.substring(token + 8);
        if (!message.startsWith(before) || !message.endsWith(after) || message.length() <= before.length() + after.length()) return OptionalLong.empty();
        long count = amount(message.substring(before.length(), message.length() - after.length()));
        return count > 0 ? OptionalLong.of(count) : OptionalLong.empty();
    }

    /** SkyOcean lists all selected positive sack changes in one comma-separated notification. */
    private static OptionalLong skyOceanGlossy(String message) {
        if (!message.startsWith(SKYOCEAN_PREFIX)) return OptionalLong.empty();
        long glossy = 0;
        // Thousands separators contain no space; item separators do.
        for (String entry : message.substring(SKYOCEAN_PREFIX.length()).split(", ", -1)) {
            var match = SKYOCEAN_ITEM.matcher(entry);
            if (!match.matches()) return OptionalLong.empty();
            if (!match.group(1).equals("glossy gemstone")) continue;
            long count = amount(match.group(2));
            if (glossy != 0 || count <= 0) return OptionalLong.empty();
            glossy = count;
        }
        return glossy > 0 ? OptionalLong.of(glossy) : OptionalLong.empty();
    }

    public static String formatFromExample(String raw) {
        String text = ChatTitleRule.clean(raw);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith(SKYOCEAN_PREFIX)) {
            return skyOceanGlossy(lower).isPresent() ? DEFAULT_GLOSSY_FORMAT : text;
        }
        if (!lower.contains("glossy gemstone")) return text;
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
