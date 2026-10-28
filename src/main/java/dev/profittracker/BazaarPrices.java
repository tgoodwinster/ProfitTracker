package dev.profittracker;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.EnumMap;
import java.util.Map;

/** Coal instant-sell bids plus gem sell-offer asks, kept distinct and quoted before fees. */
public record BazaarPrices(double coal, double enchantedCoal, double enchantedCoalBlock, long updatedAt,
                           Map<Gemstone, Double> flawless, double glossy,
                           Map<Gemstone, Double> fineOffers, Map<Gemstone, Double> flawlessOffers, double glossyOffer) {
    public BazaarPrices {
        flawless = Map.copyOf(flawless);
        fineOffers = Map.copyOf(fineOffers);
        flawlessOffers = Map.copyOf(flawlessOffers);
    }
    public BazaarPrices(double coal, double enchantedCoal, double enchantedCoalBlock, long updatedAt,
                        Map<Gemstone, Double> flawless, double glossy) {
        this(coal, enchantedCoal, enchantedCoalBlock, updatedAt, flawless, glossy, Map.of(), Map.of(), Double.NaN);
    }
    public static BazaarPrices parse(String json, long now) {
        // Skip unrelated products instead of allocating a JSON tree for the entire market.
        boolean success = false;
        long updated = 0;
        double[] prices = {Double.NaN, Double.NaN, Double.NaN};
        var gems = new EnumMap<Gemstone, Double>(Gemstone.class);
        var fineOffers = new EnumMap<Gemstone, Double>(Gemstone.class);
        var flawlessOffers = new EnumMap<Gemstone, Double>(Gemstone.class);
        double glossy = Double.NaN;
        double glossyOffer = Double.NaN;
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
                            if (index >= 0) prices[index] = price(reader).bid();
                            else if (product.equals("GLOSSY_GEMSTONE")) {
                                var quote = price(reader);
                                glossy = quote.bid();
                                glossyOffer = quote.ask();
                            }
                            else if (product.length() > 9 && product.startsWith("FINE_") && product.endsWith("_GEM")) {
                                var gemstone = Gemstone.named(product.substring(5, product.length() - 4));
                                if (gemstone.isPresent()) {
                                    double offer = price(reader).ask();
                                    if (Double.isFinite(offer) && offer > 0) fineOffers.put(gemstone.get(), offer);
                                } else reader.skipValue();
                            }
                            else if (product.length() > 13 && product.startsWith("FLAWLESS_") && product.endsWith("_GEM")) {
                                var gemstone = Gemstone.named(product.substring(9, product.length() - 4));
                                if (gemstone.isPresent()) {
                                    var quote = price(reader);
                                    if (Double.isFinite(quote.bid()) && quote.bid() > 0) gems.put(gemstone.get(), quote.bid());
                                    if (Double.isFinite(quote.ask()) && quote.ask() > 0) flawlessOffers.put(gemstone.get(), quote.ask());
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
        return new BazaarPrices(prices[0], prices[1], prices[2], updated, gems, glossy, fineOffers, flawlessOffers, glossyOffer);
    }

    private record ProductPrices(double bid, double ask) { }
    private static ProductPrices price(JsonReader reader) throws IOException {
        double bid = Double.NaN, ask = Double.NaN;
        reader.beginObject();
        while (reader.hasNext()) {
            // sell_summary = instant-sell bids; buy_summary = existing sell offers.
            switch (reader.nextName()) {
                case "sell_summary" -> bid = orders(reader, false);
                case "buy_summary" -> ask = orders(reader, true);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        return new ProductPrices(bid, ask);
    }

    private static double orders(JsonReader reader, boolean offers) throws IOException {
        double best = offers ? Double.POSITIVE_INFINITY : 0;
        boolean valid = true;
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
            else best = offers ? Math.min(best, price) : Math.max(best, price);
        }
        reader.endArray();
        return valid && Double.isFinite(best) && best > 0 ? best : Double.NaN;
    }

    public double value(CoalStock shares) {
        return shares.valueAsShares(coal, enchantedCoal, enchantedCoalBlock);
    }
}
