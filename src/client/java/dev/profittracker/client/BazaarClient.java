package dev.profittracker.client;

import dev.profittracker.BazaarPrices;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BazaarClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("tgoodutils");
    private static final URI ENDPOINT = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TgoodUtils-Bazaar");
        thread.setDaemon(true);
        return thread;
    });
    private volatile BazaarPrices prices;
    private volatile boolean fetching;
    private volatile boolean failed;
    private long nextFetch;

    void refresh(long monotonicNow) {
        if (fetching || monotonicNow < nextFetch) return;
        nextFetch = monotonicNow + 60_000;
        fetching = true;
        worker.execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "TgoodUtils/1.1.1").GET().build();
                HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var body = response.body()) {
                    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                    byte[] bytes = body.readNBytes(8 * 1024 * 1024 + 1);
                    if (bytes.length > 8 * 1024 * 1024) throw new IOException("Oversized Bazaar response");
                    prices = BazaarPrices.parse(new String(bytes, StandardCharsets.UTF_8), System.currentTimeMillis());
                }
                failed = false;
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                failed = true;
                LOGGER.warn("Bazaar prices unavailable: {}", ex.toString());
            } finally { fetching = false; }
        });
    }

    BazaarPrices usable() {
        BazaarPrices current = prices;
        return current != null && System.currentTimeMillis() - current.updatedAt() <= 300_000 ? current : null;
    }

    String status() {
        BazaarPrices current = usable();
        if (current == null) return prices == null ? "Prices unavailable" : "Prices expired";
        return failed ? "Cached prices (retrying)" : "Bazaar instant-sell, before tax";
    }
}
