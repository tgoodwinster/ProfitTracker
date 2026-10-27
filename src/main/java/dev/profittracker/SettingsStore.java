package dev.profittracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public SettingsStore(Path file) { this.file = file; }

    public TrackerSettings load() throws IOException {
        if (!Files.exists(file)) return new TrackerSettings();
        try {
            TrackerSettings settings = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TrackerSettings.class);
            if (settings == null) throw new IllegalArgumentException("Empty configuration");
            settings.sanitize();
            return settings;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid tracker settings", ex);
        }
    }

    public void save(TrackerSettings settings) throws IOException {
        settings.sanitize();
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, "profittracker-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(settings), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
