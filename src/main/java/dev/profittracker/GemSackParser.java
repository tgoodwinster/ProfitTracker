package dev.profittracker;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Read only item-level Rough gains; Flawed sack entries duplicate the pristine chat feed. */
public final class GemSackParser {
    private static final Pattern LINE = Pattern.compile("^\\+([1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+) [^\\p{L}\\p{N}]{0,8}Rough ([A-Za-z]+) Gemstone \\(Gemstones Sack\\)$");
    private GemSackParser() { }

    public static Map<Gemstone, Long> rough(String message, List<String> hoverTexts) {
        if (!ChatTitleRule.clean(message).startsWith("[Sacks]")) return Map.of();
        var gains = new EnumMap<Gemstone, Long>(Gemstone.class);
        for (String hover : hoverTexts) {
            String clean = SackParser.clean(hover);
            // Any gemstone withdrawal or truncated batch makes the origin ambiguous.
            if (clean.contains("other items") || clean.startsWith("Removed") && clean.contains("Gemstone")) return Map.of();
            if (!clean.startsWith("Added items:")) continue;
            for (String line : clean.split("\\R")) {
                String text = ChatTitleRule.clean(line);
                if (!text.contains("Rough") || !text.contains("Gemstone")) continue;
                var match = LINE.matcher(text);
                if (!match.matches()) return Map.of();
                var gem = Gemstone.named(match.group(2));
                if (gem.isEmpty()) return Map.of();
                try {
                    long count = Long.parseLong(match.group(1).replace(",", ""));
                    if (count > 1_000_000 || gains.containsKey(gem.get())) return Map.of();
                    gains.put(gem.get(), count);
                } catch (NumberFormatException ex) { return Map.of(); }
            }
        }
        return Map.copyOf(gains);
    }
}
