package dev.profittracker;

import java.util.EnumMap;
import java.util.Map;

/** Compare the three requested selling routes in the same Flawed-gemstone units. */
public record GemPrices(Map<Gemstone, Sale> best, double glossy) {
    public GemPrices { best = Map.copyOf(best); }
    public enum Route {
        FINE_NPC("Fine NPC"), FINE_OFFER("Fine BZ offer"), FLAWLESS_OFFER("Flawless BZ offer");
        public final String label;
        Route(String label) { this.label = label; }
    }
    public record Sale(Route route, double itemPrice, int flawedPerItem) {
        public double perFlawed() { return itemPrice / flawedPerItem; }
    }

    public static GemPrices choose(BazaarPrices bazaar, NpcPrices npc) {
        var result = new EnumMap<Gemstone, Sale>(Gemstone.class);
        for (var gem : Gemstone.values()) {
            Sale sale = null;
            if (npc != null) sale = better(sale, Route.FINE_NPC, npc.fine().getOrDefault(gem, Double.NaN), Gemstone.FLAWED_PER_FINE);
            if (bazaar != null) {
                sale = better(sale, Route.FINE_OFFER, bazaar.fineOffers().getOrDefault(gem, Double.NaN), Gemstone.FLAWED_PER_FINE);
                sale = better(sale, Route.FLAWLESS_OFFER, bazaar.flawlessOffers().getOrDefault(gem, Double.NaN), Gemstone.FLAWED_PER_FLAWLESS);
            }
            if (sale != null) result.put(gem, sale);
        }
        return new GemPrices(result, bazaar == null ? Double.NaN : bazaar.glossyOffer());
    }

    private static Sale better(Sale current, Route route, double price, int units) {
        if (!Double.isFinite(price) || price <= 0) return current;
        var next = new Sale(route, price, units);
        return current == null || next.perFlawed() > current.perFlawed() ? next : current;
    }
}
