package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BazaarPricesTest {
    private String response(String orders, long updated) {
        String product = "{\"sell_summary\":" + orders
                + ",\"buy_summary\":[{\"pricePerUnit\":99999,\"amount\":100}],\"quick_status\":{\"sellPrice\":99999}}";
        return "{\"success\":true,\"lastUpdated\":" + updated + ",\"products\":{\"COAL\":" + product
                + ",\"ENCHANTED_COAL\":" + product + ",\"ENCHANTED_COAL_BLOCK\":" + product + "}}";
    }

    @Test void instantSellUsesSellSummaryNotQuickStatusOrBuySummary() {
        var prices = BazaarPrices.parse(response("[{\"pricePerUnit\":5,\"amount\":100},{\"pricePerUnit\":7,\"amount\":30}]", 100000), 100000);
        assertEquals(7, prices.coal());
        assertEquals(70, prices.value(new CoalStock(10, 0, 0)));
    }

    @Test void rejectsMissingEmptyInvalidStaleAndFailedData() {
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse("{}", 100000));
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse("{\"success\":false}", 100000));
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse(response("[]", 100000), 100000));
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse(response("[{\"pricePerUnit\":-1,\"amount\":100}]", 100000), 100000));
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse(response("[{\"pricePerUnit\":7,\"amount\":100}]", 100000), 500001));
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse(response("[{\"pricePerUnit\":7,\"amount\":100}]", 200000), 100000));
    }

    @Test void streamingParserSkipsUnrelatedProductsAndAcceptsReorderedRootFields() {
        String product = "{\"extra\":[1,{\"lots\":[2,3]}],\"sell_summary\":[{\"amount\":100,\"orders\":2,\"pricePerUnit\":9}]}";
        String json = "{\"products\":{\"DIAMOND\":{\"ignored\":[{\"pricePerUnit\":99999}]},\"COAL\":"
                + product + ",\"ENCHANTED_COAL\":" + product + ",\"ENCHANTED_COAL_BLOCK\":" + product
                + "},\"lastUpdated\":100000,\"success\":true}";
        assertEquals(9, BazaarPrices.parse(json, 100000).coal());
    }

    @Test void malformedOrIncompleteOrdersAreStillRejected() {
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse(response("[{\"amount\":100}]", 100000), 100000));
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse(response("[{\"pricePerUnit\":7}]", 100000), 100000));
        assertThrows(RuntimeException.class, () -> BazaarPrices.parse("{\"success\":true,\"lastUpdated\":100000,\"products\":{}}", 100000));
    }
}
