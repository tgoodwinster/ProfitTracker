package dev.profittracker;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.EnumMap;
import java.util.Map;

/** Best buy order = what an instant seller receives, before Bazaar tax. */
public record BazaarPrices(double coal, double enchantedCoal, double enchantedCoalBlock, long updatedAt,
                           Map<Gemstone, Double> flawless, double glossy) {
    public BazaarPrices { flawless = Map.copyOf(flawless); }
    public static BazaarPrices parse(String json, long now) {
        // Skip unrelated products instead of allocating a JSON tree for the entire market.
        boolean success = false;
        long updated = 0;
        double[] prices = {Double.NaN, Double.NaN, Double.NaN};
        var gems = new EnumMap<Gemstone, Double>(Gemstone.class);
        double glossy = Double.NaN;
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "success" -> success = reader.nextBoolean();
                    case "lastUpdated" -> updated = reader.nextLong();
                    case "products" -> {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String product = reader.nextName();
                            int index = switch (product) {
                                case "COAL" -> 0;
                                case "ENCHANTED_COAL" -> 1;
                                case "ENCHANTED_COAL_BLOCK" -> 2;
                                default -> -1;
                            };
                            if (index >= 0) prices[index] = price(reader);
                            else if (product.equals("GLOSSY_GEMSTONE")) glossy = price(reader);
                            else if (product.length() > 13 && product.startsWith("FLAWLESS_") && product.endsWith("_GEM")) {
                                var gemstone = Gemstone.named(product.substring(9, product.length() - 4));
                                if (gemstone.isPresent()) {
                                    double value = price(reader);
                                    if (Double.isFinite(value) && value > 0) gems.put(gemstone.get(), value);
                                } else reader.skipValue();
                            } else reader.skipValue();
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
        return new BazaarPrices(prices[0], prices[1], prices[2], updated, gems, glossy);
    }

    private static double price(JsonReader reader) throws IOException {
        // Hypixel names summaries for the instant action: sell_summary is what sellers receive.
        double best = 0;
        boolean valid = true;
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
                if (!Double.isFinite(price) || price <= 0 || amount <= 0) valid = false;
                else best = Math.max(best, price);
            }
            reader.endArray();
        }
        reader.endObject();
        return valid && best > 0 ? best : Double.NaN;
    }

    public double value(CoalStock shares) {
        return shares.valueAsShares(coal, enchantedCoal, enchantedCoalBlock);
    }
}
