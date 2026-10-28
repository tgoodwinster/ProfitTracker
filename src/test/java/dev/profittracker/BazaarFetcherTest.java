package dev.profittracker;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BazaarFetcherTest {
    private HttpServer server;
    private ExecutorService worker;
    private HttpClient client;
    private BazaarFetcher fetcher;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        worker = Executors.newCachedThreadPool();
        server.setExecutor(worker);
        server.createContext("/ok", exchange -> {
            String product = "{\"sell_summary\":[{\"pricePerUnit\":7,\"amount\":100}]}";
            String json = "{\"success\":true,\"lastUpdated\":" + System.currentTimeMillis()
                    + ",\"products\":{\"COAL\":" + product + ",\"ENCHANTED_COAL\":" + product
                    + ",\"ENCHANTED_COAL_BLOCK\":" + product + "}}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        fetcher = new BazaarFetcher(client);
    }

    @AfterEach void stop() {
        server.stop(0);
        worker.shutdownNow();
        client.shutdownNow();
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }

    @Test void successfulResponseParsesPrices() throws Exception {
        var prices = fetcher.fetch(uri("/ok"), Duration.ofSeconds(3));
        assertEquals(7, prices.coal());
    }

    @Test void publicItemResourceUsesTheSameBoundedGzipFetcher() throws Exception {
        server.createContext("/items", exchange -> {
            byte[] bytes = gzip("{\"success\":true,\"items\":[{\"id\":\"FINE_JADE_GEM\",\"npc_sell_price\":19200}]}".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        assertEquals(19_200, fetcher.fetchNpc(uri("/items"), Duration.ofSeconds(3)).fine().get(Gemstone.JADE));
    }

    @Test void deadlineIncludesAStalledResponseBodyAndAllowsRecovery() throws Exception {
        server.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            try (var output = exchange.getResponseBody()) {
                output.write('{'); output.flush();
                try { Thread.sleep(2_000); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            } catch (IOException ignored) { /* Expected when the client cancels. */ }
        });
        fetcher.fetch(uri("/ok"), Duration.ofSeconds(3)); // Warm the HTTP client before timing the body timeout.
        long start = System.nanoTime();
        assertThrows(IOException.class, () -> fetcher.fetch(uri("/slow"), Duration.ofMillis(200)));
        assertTrue(System.nanoTime() - start < Duration.ofSeconds(2).toNanos());
        assertEquals(7, fetcher.fetch(uri("/ok"), Duration.ofSeconds(3)).coal());
    }

    @Test void oversizedResponseIsRejectedBeforeParsing() {
        server.createContext("/huge", exchange -> {
            exchange.sendResponseHeaders(200, BazaarFetcher.MAX_BYTES + 1L);
            try (var output = exchange.getResponseBody()) { output.write(new byte[BazaarFetcher.MAX_BYTES + 1]); }
            catch (IOException ignored) { /* The size limit cancels the download. */ }
        });
        assertThrows(IOException.class, () -> fetcher.fetch(uri("/huge"), Duration.ofSeconds(3)));
    }

    @Test void gzipNegotiationAndDecodingWork() throws Exception {
        server.createContext("/gzip", exchange -> {
            assertEquals("gzip", exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            String item = "{\"sell_summary\":[{\"amount\":100,\"pricePerUnit\":9}]}";
            String text = "{\"success\":true,\"lastUpdated\":" + System.currentTimeMillis()
                    + ",\"products\":{\"COAL\":" + item + ",\"ENCHANTED_COAL\":" + item
                    + ",\"ENCHANTED_COAL_BLOCK\":" + item + "}}";
            byte[] bytes = gzip(text.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        assertEquals(9, fetcher.fetch(uri("/gzip"), Duration.ofSeconds(3)).coal());
    }

    @Test void expandedGzipResponseIsAlsoLimited() {
        server.createContext("/bomb", exchange -> {
            byte[] bytes = gzip(new byte[BazaarFetcher.MAX_BYTES + 1]);
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        assertThrows(IOException.class, () -> fetcher.fetch(uri("/bomb"), Duration.ofSeconds(3)));
    }

    private byte[] gzip(byte[] bytes) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) { gzip.write(bytes); }
        return output.toByteArray();
    }
}
