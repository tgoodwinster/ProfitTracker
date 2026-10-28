package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcPricesTest {
    @Test void readsRealFineNpcValuesAndSkipsUnrelatedItemMetadata() {
        var prices = NpcPrices.parse("""
                {"success":true,"items":[
                {"id":"FINE_JADE_GEM","name":"Fine Jade Gemstone","npc_sell_price":19200,"lots":[{"skip":true}]},
                {"npc_sell_price":25600,"id":"FINE_JASPER_GEM"},
                {"id":"FLAWLESS_JADE_GEM","npc_sell_price":9999999},
                {"id":"FINE_UNKNOWN_GEM","npc_sell_price":9999999},
                {"id":"FINE_RUBY_GEM","npc_sell_price":-1}]}
                """, 123);
        assertEquals(2, prices.fine().size());
        assertEquals(19_200, prices.fine().get(Gemstone.JADE));
        assertEquals(25_600, prices.fine().get(Gemstone.JASPER));
        assertEquals(123, prices.fetchedAt());
        assertThrows(UnsupportedOperationException.class, () -> prices.fine().clear());
    }

    @Test void failedEmptyAndMalformedResourcesNeverBecomeGuessedNpcPrices() {
        for (String json : new String[]{"{}", "{\"success\":false,\"items\":[]}", "{\"success\":true,\"items\":[]}", "{"}) {
            assertThrows(RuntimeException.class, () -> NpcPrices.parse(json, 123));
        }
    }
}
