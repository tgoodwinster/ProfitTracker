package dev.profittracker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Called only on the price worker. Both response size and total header/body time are bounded. */
public final class BazaarFetcher {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final HttpClient client;

    public BazaarFetcher(HttpClient client) { this.client = client; }

    public BazaarPrices fetch(URI endpoint, Duration timeout) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("User-Agent", "TgoodUtils/1.2.1").GET().build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_BYTES));
        final HttpResponse<byte[]> response;
        try {
            response = pending.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            pending.cancel(true);
            throw new IOException("Bazaar header/body deadline exceeded", ex);
        } catch (InterruptedException ex) {
            pending.cancel(true);
            throw ex;
        } catch (ExecutionException ex) {
            throw new IOException("Bazaar request failed", ex.getCause());
        }
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        return BazaarPrices.parse(new String(response.body(), StandardCharsets.UTF_8), System.currentTimeMillis());
    }
}
