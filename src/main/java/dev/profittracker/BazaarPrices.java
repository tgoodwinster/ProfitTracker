package dev.profittracker;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;

/** Best buy order = what an instant seller receives, before Bazaar tax. */
public record BazaarPrices(double coal, double enchantedCoal, double enchantedCoalBlock, long updatedAt) {
    public static BazaarPrices parse(String json, long now) {
        // Skip unrelated products instead of allocating a JSON tree for the entire market.
        boolean success = false;
        long updated = 0;
        double[] prices = {Double.NaN, Double.NaN, Double.NaN};
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "success" -> success = reader.nextBoolean();
                    case "lastUpdated" -> updated = reader.nextLong();
                    case "products" -> {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            int index = switch (reader.nextName()) {
                                case "COAL" -> 0;
                                case "ENCHANTED_COAL" -> 1;
                                case "ENCHANTED_COAL_BLOCK" -> 2;
                                default -> -1;
                            };
                            if (index < 0) reader.skipValue();
                            else prices[index] = price(reader);
                        }
                        reader.endObject();
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing Bazaar data");
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalArgumentException("Invalid Bazaar response", ex);
        }
        if (!success) throw new IllegalArgumentException("Bazaar API did not report success");
        if (updated <= 0 || updated > now + 60_000 || now - updated > 300_000) {
            throw new IllegalArgumentException("Bazaar API returned stale or invalid data");
        }
        for (double value : prices) if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Missing coal buy orders");
        }
        return new BazaarPrices(prices[0], prices[1], prices[2], updated);
    }

    private static double price(JsonReader reader) throws IOException {
        // Hypixel names summaries for the instant action: sell_summary is what sellers receive.
        double best = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            if (!reader.nextName().equals("sell_summary")) { reader.skipValue(); continue; }
            reader.beginArray();
            while (reader.hasNext()) {
                double price = Double.NaN;
                long amount = 0;
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "pricePerUnit" -> price = reader.nextDouble();
                        case "amount" -> amount = reader.nextLong();
                        default -> reader.skipValue();
                    }
                }
                reader.endObject();
                if (!Double.isFinite(price) || price <= 0 || amount <= 0) throw new IllegalArgumentException("Invalid coal buy order");
                best = Math.max(best, price);
            }
            reader.endArray();
        }
        reader.endObject();
        if (best == 0) throw new IllegalArgumentException("No coal buy orders");
        return best;
    }

    public double value(CoalStock shares) {
        return shares.valueAsShares(coal, enchantedCoal, enchantedCoalBlock);
    }
}
