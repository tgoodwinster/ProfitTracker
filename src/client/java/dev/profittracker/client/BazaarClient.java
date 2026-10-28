package dev.profittracker.client;

import dev.profittracker.BazaarPrices;
import dev.profittracker.BazaarFetcher;
import dev.profittracker.GemPrices;
import dev.profittracker.NpcPrices;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BazaarClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgoodutils");
    private static final URI ENDPOINT = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
    private static final URI ITEMS = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
    private BazaarFetcher fetcher; // Initialized and used exclusively on the worker, including TLS setup.
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TgoodUtils-Bazaar");
        thread.setDaemon(true);
        return thread;
    });
    private volatile BazaarPrices prices;
    private volatile NpcPrices npcPrices;
    private volatile boolean fetching;
    private volatile boolean failed;
    private long nextFetch;

    void refresh(long monotonicNow, boolean needGemPrices) {
        if (fetching || monotonicNow < nextFetch) return;
        nextFetch = monotonicNow + 60_000;
        fetching = true;
        worker.execute(() -> {
            try {
                if (fetcher == null) fetcher = new BazaarFetcher(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
                prices = fetcher.fetch(ENDPOINT, Duration.ofSeconds(15));
                failed = false;
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                failed = true;
                LOGGER.warn("Bazaar prices unavailable: {}", ex.toString());
            }
            try {
                var npc = npcPrices;
                if (needGemPrices && fetcher != null && (npc == null || System.currentTimeMillis() - npc.fetchedAt() >= 3_600_000)) {
                    npcPrices = fetcher.fetchNpc(ITEMS, Duration.ofSeconds(15));
                }
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                LOGGER.warn("NPC gemstone prices unavailable: {}", ex.toString());
            } finally { fetching = false; }
        });
    }

    BazaarPrices usable() {
        BazaarPrices current = prices;
        return current != null && System.currentTimeMillis() - current.updatedAt() <= 300_000 ? current : null;
    }

    GemPrices gems() {
        var npc = npcPrices;
        return GemPrices.choose(usable(), npc != null && System.currentTimeMillis() - npc.fetchedAt() < 86_400_000 ? npc : null);
    }

    String status() {
        BazaarPrices current = usable();
        if (current == null) return prices == null ? "Prices unavailable" : "Prices expired";
        return failed ? "Cached prices (retrying)" : "Bazaar instant-sell, before tax";
    }
}
