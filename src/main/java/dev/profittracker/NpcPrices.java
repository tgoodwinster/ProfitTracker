package dev.profittracker;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.EnumMap;
import java.util.Map;

/** Fine gemstone NPC prices from Hypixel's public items resource, never guessed constants. */
public record NpcPrices(Map<Gemstone, Double> fine, long fetchedAt) {
    public NpcPrices { fine = Map.copyOf(fine); }

    public static NpcPrices parse(String json, long now) {
        boolean success = false;
        var prices = new EnumMap<Gemstone, Double>(Gemstone.class);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "success" -> success = reader.nextBoolean();
                    case "items" -> {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            String id = "";
                            double price = Double.NaN;
                            reader.beginObject();
                            while (reader.hasNext()) {
                                switch (reader.nextName()) {
                                    case "id" -> id = reader.nextString();
                                    case "npc_sell_price" -> {
                                        if (reader.peek() == JsonToken.NUMBER) price = reader.nextDouble();
                                        else reader.skipValue();
                                    }
                                    default -> reader.skipValue();
                                }
                            }
                            reader.endObject();
                            if (id.startsWith("FINE_") && id.endsWith("_GEM") && id.length() > 9
                                    && Double.isFinite(price) && price > 0) {
                                var gem = Gemstone.named(id.substring(5, id.length() - 4));
                                if (gem.isPresent()) prices.put(gem.get(), price);
                            }
                        }
                        reader.endArray();
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing item data");
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalArgumentException("Invalid item resource", ex);
        }
        if (!success || prices.isEmpty()) throw new IllegalArgumentException("Missing Fine gemstone NPC prices");
        return new NpcPrices(prices, now);
    }
}
