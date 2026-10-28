package dev.profittracker;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GemPricesTest {
    private BazaarPrices market(double fine, double flawless) {
        return new BazaarPrices(1, 1, 1, 100, Map.of(Gemstone.JADE, 10d), 10,
                Map.of(Gemstone.JADE, fine), Map.of(Gemstone.JADE, flawless), 500);
    }
    private NpcPrices npc(double price) { return new NpcPrices(Map.of(Gemstone.JADE, price), 100); }

    @Test void comparesCraftingRatiosInsteadOfComparingItemStickerPrices() {
        var prices = GemPrices.choose(market(20_000, 1_536_000), npc(19_200));
        var sale = prices.best().get(Gemstone.JADE);
        assertEquals(GemPrices.Route.FINE_OFFER, sale.route());
        assertEquals(250, sale.perFlawed());
        assertEquals(500, prices.glossy());
    }

    @Test void eachOfTheThreeRoutesCanWinAndTiesPreferNpc() {
        assertEquals(GemPrices.Route.FINE_NPC, GemPrices.choose(market(24_500, 1_900_000), npc(25_600)).best().get(Gemstone.JADE).route());
        assertEquals(GemPrices.Route.FLAWLESS_OFFER, GemPrices.choose(market(20_000, 2_560_000), npc(19_200)).best().get(Gemstone.JADE).route());
        assertEquals(GemPrices.Route.FINE_NPC, GemPrices.choose(market(19_200, 1_536_000), npc(19_200)).best().get(Gemstone.JADE).route());
    }

    @Test void missingMarketsFallBackOnlyToObservedValidRoutes() {
        assertEquals(GemPrices.Route.FINE_NPC, GemPrices.choose(null, npc(19_200)).best().get(Gemstone.JADE).route());
        assertEquals(GemPrices.Route.FINE_OFFER, GemPrices.choose(market(20_000, Double.NaN), null).best().get(Gemstone.JADE).route());
        assertTrue(GemPrices.choose(market(-1, Double.POSITIVE_INFINITY), null).best().isEmpty());
        assertTrue(GemPrices.choose(null, null).best().isEmpty());
    }
}
