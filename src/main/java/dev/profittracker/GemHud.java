package dev.profittracker;

import java.util.EnumSet;
import java.util.List;

/** Four short lines; counters and price diagnostics stay out of the gameplay display. */
public final class GemHud {
    private GemHud() { }
    public static List<String> lines(GemSession session, GemPrices prices, GemSettings settings) {
        var total = session.value(prices, settings.includeGlossy);
        var rate = session.coinsPerHour(prices, settings.includeGlossy, settings.averageMinutes);
        long remaining = Math.max(0, Math.clamp(settings.averageMinutes, 1, 10) * 60_000L - session.rateMillis());
        var gems = EnumSet.noneOf(Gemstone.class);
        gems.addAll(session.flawed().keySet());
        gems.addAll(session.rough().keySet());
        String route = "Best sale";
        if (prices != null) {
            var routes = new java.util.HashSet<GemPrices.Route>();
            for (var gem : gems) {
                var sale = prices.best().get(gem);
                if (sale != null) routes.add(sale.route());
            }
            if (routes.size() == 1) route = routes.iterator().next().label;
            else if (routes.size() > 1) route = "Best sales";
        }
        double flawedEquivalent = session.flawedTotal() + session.roughTotal() / (double) Gemstone.ROUGH_PER_FLAWED;
        return List.of(
                "Gemstones | " + CompactNumbers.duration(session.rateMillis()) + (session.running() ? "" : " | Paused"),
                (rate.isPresent() ? CompactNumbers.format(rate.getAsDouble()) : "--") + "/h | "
                        + (total.isPresent() ? CompactNumbers.format(total.getAsDouble()) : "--") + " total",
                CompactNumbers.format(flawedEquivalent) + " flawed eq"
                        + (settings.includeGlossy ? " | " + CompactNumbers.format(session.glossy()) + " glossy" : ""),
                total.isEmpty() ? "Waiting for prices" : remaining > 0
                        ? settings.averageMinutes + "m average ready in " + CompactNumbers.duration(remaining)
                        : route + " | " + settings.averageMinutes + "m avg");
    }
}
