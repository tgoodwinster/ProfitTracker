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
}
