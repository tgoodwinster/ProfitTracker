package dev.profittracker;

import java.io.ByteArrayInputStream;
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
import java.util.zip.GZIPInputStream;

/** API key is sent only in the API-Key header, never a URL or diagnostic message. */
public final class BestiaryFetcher {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private final HttpClient client;
    public BestiaryFetcher(HttpClient client) { this.client = client; }
    public BestiaryApiData fetch(URI endpoint, String key, String uuid, String profile) throws IOException, InterruptedException {
        if (!PrivateApiKeyStore.valid(key)) throw new IOException("Invalid API key format");
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("API-Key", key).header("Accept-Encoding", "gzip").header("User-Agent", "TgoodUtils/1.7.0").GET().build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_BYTES));
        final HttpResponse<byte[]> response;
        try { response = pending.get(15, TimeUnit.SECONDS); }
        catch (TimeoutException ex) { pending.cancel(true); throw new IOException("Bestiary request deadline exceeded"); }
        catch (InterruptedException ex) { pending.cancel(true); throw ex; }
        catch (ExecutionException ex) { throw new IOException("Bestiary request failed"); }
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        byte[] body = response.body();
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        if (encoding.equalsIgnoreCase("gzip")) {
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(body))) { body = gzip.readNBytes(MAX_BYTES + 1); }
        } else if (!encoding.isEmpty() && !encoding.equalsIgnoreCase("identity")) throw new IOException("Unsupported response encoding");
        if (body.length > MAX_BYTES) throw new IOException("Bestiary response exceeds limit");
        return BestiaryApiData.parse(new String(body, StandardCharsets.UTF_8), uuid, profile);
    }
}
