package dev.profittracker.client;

import dev.profittracker.BestiaryApiData;
import dev.profittracker.BestiaryFetcher;
import dev.profittracker.PrivateApiKeyStore;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/** One background request per five minutes, scoped to the connected account and active profile. */
final class BestiaryClient {
    record Snapshot(String player, BestiaryApiData data, long sequence) { }
    private final PrivateApiKeyStore store;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "TgoodUtils-Bestiary"); thread.setDaemon(true); return thread;
    });
    private volatile String key = "";
    private volatile String status = "API key required";
    private volatile Snapshot snapshot;
    private volatile boolean fetching;
    private volatile long generation;
    private String context = "";
    private long nextFetch, sequence;
    private BestiaryFetcher fetcher;
    BestiaryClient(PrivateApiKeyStore store) {
        this.store = store;
        try { key = store.load(); status = key.isEmpty() ? "API key required" : "Waiting for SkyBlock"; }
        catch (IOException ignored) { status = "Private key file could not be read"; }
    }
    boolean hasKey() { return !key.isEmpty(); }
    String status() { return status; }
    Snapshot snapshot() { return snapshot; }
    void setKey(String value) throws IOException {
        store.save(value); key = value; generation++; snapshot = null; nextFetch = 0;
        status = value.isEmpty() ? "API key required" : "Waiting to sync";
    }
    boolean refresh(String uuid, String profile, long now) {
        String requestedContext = uuid + ":" + profile;
        if (!context.equals(requestedContext)) { context = requestedContext; generation++; snapshot = null; nextFetch = 0; }
        if (worker.isShutdown() || key.isEmpty() || fetching || now < nextFetch) return false;
        nextFetch = now + 300_000; fetching = true; status = "Syncing Bestiary...";
        long requestGeneration = generation;
        String requestKey = key;
        worker.execute(() -> {
            try {
                if (fetcher == null) fetcher = new BestiaryFetcher(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
                var endpoint = URI.create("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuid.replace("-", ""));
                var data = fetcher.fetch(endpoint, requestKey, uuid, profile);
                if (requestGeneration == generation) { snapshot = new Snapshot(uuid, data, ++sequence); status = "Bestiary synced"; }
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                if (requestGeneration == generation) status = switch (ex.getMessage() == null ? "" : ex.getMessage()) {
                    case "HTTP 403", "HTTP 401" -> "Key rejected or expired";
                    case "HTTP 429" -> "API limit; retry in 5 min";
                    default -> "Sync unavailable; retry in 5 min";
                };
                // Do not log HTTP objects, response bodies, or exception traces containing credentials.
            } finally { fetching = false; }
        });
        return true;
    }
    void requestNow() { nextFetch = 0; }
    void disconnect() { generation++; snapshot = null; context = ""; nextFetch = 0; }
    void close() { worker.shutdownNow(); }
}
