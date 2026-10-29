package dev.profittracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** Separate from shareable module settings. Never serialize this object into TrackerSettings. */
public final class PrivateApiKeyStore {
    private final Path file;
    public PrivateApiKeyStore(Path file) { this.file = file; }
    public static boolean valid(String key) { return key != null && key.matches("[A-Za-z0-9_-]{16,128}"); }
    public String load() throws IOException {
        if (!Files.exists(file)) return "";
        try {
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String key = json.get("hypixelKey").getAsString();
            if (!valid(key)) throw new IllegalArgumentException();
            return key;
        } catch (RuntimeException ex) { throw new IOException("Private API key file is invalid"); }
    }
    public void save(String key) throws IOException {
        if (key.isBlank()) { Files.deleteIfExists(file); return; }
        if (!valid(key)) throw new IOException("Invalid API key format");
        Files.createDirectories(file.getParent());
        var json = new JsonObject(); json.addProperty("hypixelKey", key);
        Path temp = Files.createTempFile(file.getParent(), "tgoodutils-private-", ".tmp");
        try {
            Files.writeString(temp, json.toString());
            try { Files.setPosixFilePermissions(temp, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
