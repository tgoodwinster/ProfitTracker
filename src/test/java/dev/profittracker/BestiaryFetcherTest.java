package dev.profittracker;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BestiaryFetcherTest {
    @Test void keyUsesOnlyHeaderAndValidCompressedProfileIsParsed() throws Exception {
        String fake = "fake" + "-".repeat(20);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0),0);
        server.createContext("/profiles", exchange -> {
            assertEquals(fake,exchange.getRequestHeaders().getFirst("API-Key"));
            assertFalse(exchange.getRequestURI().toString().contains(fake));
            String body = "{\"success\":true,\"profiles\":[{\"profile_id\":\"p\",\"cute_name\":\"Blueberry\",\"selected\":true,\"members\":{\"mine\":{\"last_save\":42,\"bestiary\":{\"kills\":{\"endermite_37\":123}}}}}]}";
            var bytes = new ByteArrayOutputStream();
            try(var gzip = new GZIPOutputStream(bytes)){gzip.write(body.getBytes(StandardCharsets.UTF_8));}
            exchange.getResponseHeaders().set("Content-Encoding","gzip");
            exchange.sendResponseHeaders(200,bytes.size());
            try(var out=exchange.getResponseBody()){out.write(bytes.toByteArray());}
        });
        server.start();
        try(var client = HttpClient.newHttpClient()) {
            var data = new BestiaryFetcher(client).fetch(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/profiles?uuid=mine"),fake,"mine","");
            assertEquals(123,data.kills());assertEquals(42,data.savedAt());
        } finally {server.stop(0);}
    }
    @Test void rejectionDoesNotEchoResponseBodiesOrCredentials() throws Exception {
        String fake="fake"+"_".repeat(20);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/reject",exchange->{
            byte[] body=("Rejected private key "+fake).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
        });server.start();
        try(var client=HttpClient.newHttpClient()){
            var endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/reject");
            var error=assertThrows(IOException.class,()->new BestiaryFetcher(client).fetch(endpoint,fake,"mine",""));
            assertEquals("HTTP 403",error.getMessage());assertFalse(error.toString().contains(fake));
        }finally{server.stop(0);}
    }
}
