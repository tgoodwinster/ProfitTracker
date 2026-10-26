package dev.profittracker;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SackParserTest {
    @Test void readsItemHoverRatherThanAggregateSummary() {
        var result = SackParser.parse("§6[Sacks] §a+99,999 items. (Last 5s.)", List.of(
                "§aAdded items:\n§a +1,024 Coal §7(Mining Sack)\n +3 Enchanted Coal (Enchanted Mining Sack)\n +2 Diamond (Mining Sack)"));
        assertEquals(new CoalStock(1024, 3, 0), result.delta());
        assertTrue(result.complete());
    }

    @Test void preservesWithdrawalSignsAndAllCoalForms() {
        var result = SackParser.parse("[Sacks] +1 -10 items", List.of(
                "Added items:\n +1 Enchanted Coal Block (Enchanted Mining Sack)",
                "Removed items:\n -10 Coal (Mining Sack)\n -1 Block of Coal (Mining Sack)"));
        assertEquals(new CoalStock(-19, 0, 1), result.delta());
    }

    @Test void ignoresOrdinaryChatAndOtherMaterials() {
        assertEquals(CoalStock.ZERO, SackParser.parse("Player: [Sacks] +100 Coal",
                List.of("Added items:\n +100 Coal (Mining Sack)")).delta());
        assertEquals(CoalStock.ZERO, SackParser.parse("[Sacks] +64 items",
                List.of("Added items:\n +64 Diamond (Mining Sack)")).delta());
    }

    @Test void doesNotGuessFromSummaryMissingHoverOrTruncation() {
        assertFalse(SackParser.parse("[Sacks] +64 items", List.of()).complete());
        assertFalse(SackParser.parse("[Sacks] +64 items", List.of("Added items:\n +3 other items")).complete());
        assertFalse(SackParser.parse("[Sacks] +64 items", List.of("Added items:\n +lots Coal (Mining Sack)")).complete());
    }
}
