package dev.profittracker;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

/** Called only on the price worker. Both response size and total header/body time are bounded. */
public final class BazaarFetcher {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final HttpClient client;

    public BazaarFetcher(HttpClient client) { this.client = client; }

    public BazaarPrices fetch(URI endpoint, Duration timeout) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("User-Agent", "TgoodUtils/1.5.2").header("Accept-Encoding", "gzip").GET().build();
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
        byte[] body = response.body();
        String encoding = response.headers().firstValue("Content-Encoding").orElse("").trim();
        if (encoding.equalsIgnoreCase("gzip")) {
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(body))) { body = gzip.readNBytes(MAX_BYTES + 1); }
            if (body.length > MAX_BYTES) throw new IOException("Expanded Bazaar response exceeds limit");
        } else if (!encoding.isEmpty() && !encoding.equalsIgnoreCase("identity")) {
            throw new IOException("Unsupported Bazaar encoding: " + encoding);
        }
        return BazaarPrices.parse(new String(body, StandardCharsets.UTF_8), System.currentTimeMillis());
    }
}
