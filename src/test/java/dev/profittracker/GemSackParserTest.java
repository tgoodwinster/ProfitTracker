package dev.profittracker;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GemSackParserTest {
    @Test void readsRoughOnlyAndDoesNotCountPristineFlawedOrGlossyTwice() {
        var gains = GemSackParser.rough("[Sacks] +99,999 items. (Last 5s.)", List.of("""
                Added items:
                  +6,577  Rough Jade Gemstone (Gemstones Sack)
                  +1,463  Flawed Jade Gemstone (Gemstones Sack)
                  +1 Glossy Gemstone (Mining Sack, Events Sack)
                  +80 Rough Ruby Gemstone (Gemstones Sack)
                """));
        assertEquals(Map.of(Gemstone.JADE, 6_577L, Gemstone.RUBY, 80L), gains);
    }
    @Test void missingAggregateQuotedTruncatedAndTransferDataIsIgnored() {
        assertTrue(GemSackParser.rough("[Sacks] +500 items", List.of()).isEmpty());
        assertTrue(GemSackParser.rough("Player: [Sacks] +500 items", List.of("Added items:\n+500 Rough Jade Gemstone (Gemstones Sack)")).isEmpty());
        for (var hover : List.of("Added items:\n+3 other items", "Added items:\n+bad Rough Jade Gemstone (Gemstones Sack)",
                "Added items:\n+99999999999999999999 Rough Jade Gemstone (Gemstones Sack)", "Added items:\n+10 Rough Unknown Gemstone (Gemstones Sack)")) {
            assertTrue(GemSackParser.rough("[Sacks] +500 items", List.of(hover)).isEmpty());
        }
        assertTrue(GemSackParser.rough("[Sacks] +80 -10 items", List.of(
                "Added items:\n+80 Rough Jade Gemstone (Gemstones Sack)",
                "Removed items:\n-10 Flawed Jade Gemstone (Gemstones Sack)")).isEmpty());
    }
}
