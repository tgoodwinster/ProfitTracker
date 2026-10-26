package dev.profittracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Best buy order = what an instant seller receives, before Bazaar tax. */
public record BazaarPrices(double coal, double enchantedCoal, double enchantedCoalBlock, long updatedAt) {
    public static BazaarPrices parse(String json, long now) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("success") || !root.get("success").getAsBoolean()) {
            throw new IllegalArgumentException("Bazaar API did not report success");
        }
        long updated = root.get("lastUpdated").getAsLong();
        if (updated <= 0 || updated > now + 60_000 || now - updated > 300_000) {
            throw new IllegalArgumentException("Bazaar API returned stale or invalid data");
        }
        JsonObject products = root.getAsJsonObject("products");
        return new BazaarPrices(price(products, "COAL"), price(products, "ENCHANTED_COAL"),
                price(products, "ENCHANTED_COAL_BLOCK"), updated);
    }

    private static double price(JsonObject products, String id) {
        // Hypixel names summaries for the instant action: sell_summary is what sellers receive.
        var orders = products.getAsJsonObject(id).getAsJsonArray("sell_summary");
        if (orders == null || orders.isEmpty()) throw new IllegalArgumentException("No buy orders for " + id);
        double best = 0;
        for (var entry : orders) {
            var order = entry.getAsJsonObject();
            double price = order.get("pricePerUnit").getAsDouble();
            if (!Double.isFinite(price) || price <= 0 || order.get("amount").getAsLong() <= 0) {
                throw new IllegalArgumentException("Invalid buy order for " + id);
            }
            best = Math.max(best, price);
        }
        return best;
    }

    public double value(CoalStock shares) {
        return shares.valueAsShares(coal, enchantedCoal, enchantedCoalBlock);
    }
}
