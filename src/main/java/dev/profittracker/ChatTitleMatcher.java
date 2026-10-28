package dev.profittracker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Literal matching only. Each rule has a short debounce against repeated server notifications. */
public final class ChatTitleMatcher {
    private final Map<String, Long> lastFired = new HashMap<>();

    public Optional<ChatTitleRule> match(List<ChatTitleRule> rules, String message, long now) {
        if (rules.isEmpty()) return Optional.empty();
        String normalized = ChatTitleRule.clean(message);
        String lowerCase = normalized.toLowerCase(java.util.Locale.ROOT);
        for (var rule : rules) {
            if (!rule.matchesNormalized(normalized, lowerCase)) continue;
            Long previous = lastFired.get(rule.id);
            if (previous != null && now - previous < 2_000) return Optional.empty();
            lastFired.put(rule.id, now);
            return Optional.of(rule);
        }
        return Optional.empty();
    }

    public void clear() { lastFired.clear(); }
}
