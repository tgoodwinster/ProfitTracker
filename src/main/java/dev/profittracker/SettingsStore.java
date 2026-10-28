package dev.profittracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private String lastWritten;

    public SettingsStore(Path file) { this.file = file; }

    public TrackerSettings load() throws IOException {
        if (!Files.exists(file)) return new TrackerSettings();
        try {
            var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            // Preserve the old fixed delay as the minimum, and add a 500 ms range on upgrade.
            if (json.isJsonObject()) {
                var books = json.getAsJsonObject().get("books");
                if (books != null && books.isJsonObject()) {
                    var profile = books.getAsJsonObject();
                    var oldDelay = profile.get("delayMs");
                    if (oldDelay != null && !oldDelay.isJsonNull() && !profile.has("minDelayMs") && !profile.has("maxDelayMs")) {
                        int min = Math.clamp(oldDelay.getAsInt(), 500, 5_000);
                        profile.addProperty("minDelayMs", min);
                        profile.addProperty("maxDelayMs", Math.min(5_000, min + 500));
                    }
                }
            }
            TrackerSettings settings = GSON.fromJson(json, TrackerSettings.class);
            if (settings == null) throw new IllegalArgumentException("Empty configuration");
            settings.sanitize();
            return settings;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid tracker settings", ex);
        }
    }

    public void save(TrackerSettings settings) throws IOException {
        settings.sanitize();
        String serialized = GSON.toJson(settings);
        if (serialized.equals(lastWritten)) return;
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, "profittracker-", ".tmp");
        try {
            Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            lastWritten = serialized;
        } finally { Files.deleteIfExists(temporary); }
    }
}
