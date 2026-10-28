package dev.profittracker;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatTitleTest {
    private ChatTitleRule rule(String target) {
        var rule = new ChatTitleRule();
        rule.target = target;
        return rule;
    }

    @Test void pastedChatIgnoresMinecraftFormattingAndWhitespace() {
        var rule = rule("Your Mining Speed Boost is now available!");
        assertTrue(rule.matches("§aYour Mining Speed Boost\u00A0is now available!"));
        assertTrue(rule.matches("YOUR MINING SPEED BOOST is now available!"));
        assertFalse(rule.matches("You used your Mining Speed Boost Pickaxe Ability!"));
    }

    @Test void exactAndContainsAndCaseOptionsAreDistinct() {
        var rule = rule("Ability ready");
        assertTrue(rule.matches("[Mining] Ability ready!"));
        rule.exact = true;
        assertFalse(rule.matches("[Mining] Ability ready!"));
        assertTrue(rule.matches("ABILITY READY"));
        rule.ignoreCase = false;
        assertFalse(rule.matches("ABILITY READY"));
        assertTrue(rule.matches("Ability ready"));
    }

    @Test void emptyDisabledAndRegexLikeTargetsNeverBecomeWildcardMatches() {
        var rule = rule("");
        assertFalse(rule.matches("anything"));
        rule.target = "§a  ";
        assertFalse(rule.matches("anything"));
        rule.target = ".*";
        assertFalse(rule.matches("anything"));
        assertTrue(rule.matches("literal .* text"));
        rule.enabled = false;
        assertFalse(rule.matches("literal .* text"));
    }

    @Test void duplicateServerMessagesDebounceButLaterCooldownsAlertAgain() {
        var rule = rule("ready");
        var matcher = new ChatTitleMatcher();
        assertEquals(rule, matcher.match(List.of(rule), "ready", 0).orElseThrow());
        assertTrue(matcher.match(List.of(rule), "ready", 500).isEmpty());
        assertTrue(matcher.match(List.of(rule), "ready", 1999).isEmpty());
        assertEquals(rule, matcher.match(List.of(rule), "ready", 2000).orElseThrow());
        matcher.clear();
        assertEquals(rule, matcher.match(List.of(rule), "ready", 2001).orElseThrow());
    }

    @Test void firstMatchingRuleWinsWithoutDuplicateAlerts() {
        var first = rule("ready");
        var second = rule("ready");
        var matcher = new ChatTitleMatcher();
        assertEquals(first, matcher.match(List.of(first, second), "ready", 0).orElseThrow());
        assertTrue(matcher.match(List.of(first, second), "ready", 100).isEmpty());
        first.enabled = false;
        assertEquals(second, matcher.match(List.of(first, second), "ready", 200).orElseThrow());
    }

    @Test void alertStyleCopySeparatesUnsavedEdits() {
        var original = rule("ready");
        var copy = original.copy();
        copy.alert.text = "changed";
        copy.alert.position.x = 0.1;
        assertEquals("READY!", original.alert.text);
        assertEquals(0.5, original.alert.position.x);
        assertEquals(original.id, copy.id);
    }
}
