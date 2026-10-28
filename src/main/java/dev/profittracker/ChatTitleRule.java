package dev.profittracker;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ChatTitleRule {
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("(?U)\\s+");
    public String id = UUID.randomUUID().toString();
    public boolean enabled = true;
    public String target = "";
    public boolean exact = false;
    public boolean ignoreCase = true;
    public AlertStyle alert = new AlertStyle();

    public ChatTitleRule() { alert.text = "READY!"; }

    public static String clean(String text) {
        if (text == null) return "";
        return SPACES.matcher(FORMATTING.matcher(text).replaceAll("").replace("\u200B", "")).replaceAll(" ").trim();
    }

    public void sanitize() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        target = clean(target);
        if (target.length() > 512) target = target.substring(0, 512);
        if (alert == null) { alert = new AlertStyle(); alert.text = "READY!"; }
        alert.sanitize();
    }

    public boolean matches(String message) {
        String normalized = clean(message);
        return matchesNormalized(normalized, normalized.toLowerCase(Locale.ROOT));
    }

    boolean matchesNormalized(String normalized, String lowerCase) {
        if (!enabled) return false;
        String needle = clean(target);
        if (needle.isEmpty()) return false;
        String haystack = ignoreCase ? lowerCase : normalized;
        if (ignoreCase) {
            needle = needle.toLowerCase(Locale.ROOT);
        }
        return exact ? haystack.equals(needle) : haystack.contains(needle);
    }

    public ChatTitleRule copy() {
        var copy = new ChatTitleRule();
        copy.id = id;
        copy.enabled = enabled;
        copy.target = target;
        copy.exact = exact;
        copy.ignoreCase = ignoreCase;
        copy.alert = alert.copy();
        return copy;
    }
}
