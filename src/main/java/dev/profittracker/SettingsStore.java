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
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();

    public SettingsStore(Path file) { this.file = file; }

    public TrackerSettings load() throws IOException {
        if (!Files.exists(file)) return new TrackerSettings();
        try {
            var json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (json.isJsonObject()) {
                var gems = json.getAsJsonObject().get("gems");
                if (gems != null && gems.isJsonObject()) {
                    var profile = gems.getAsJsonObject();
                    if (!profile.has("timerModeVersion")) {
                        // Apply the user's all-running-time choice to settings saved by 1.5.0 too.
                        profile.addProperty("miningTimeOnly", false);
                    }
                    if (!profile.has("glossyFormatVersion")) {
                        var format = profile.get("glossyFormat");
                        // Replace only the old built-in default; keep custom notification formats.
                        if (format != null && format.isJsonPrimitive() && format.getAsJsonPrimitive().isString()
                                && ChatTitleRule.clean(format.getAsString()).equalsIgnoreCase(GemMessages.LEGACY_GLOSSY_FORMAT)) {
                            profile.addProperty("glossyFormat", GemMessages.DEFAULT_GLOSSY_FORMAT);
                        }
                    }
                }
                var books = json.getAsJsonObject().get("books");
                if (books != null && books.isJsonObject()) {
                    var profile = books.getAsJsonObject();
                    var oldDelay = profile.get("delayMs");
                    // Preserve the old fixed delay as the minimum, and add a 500 ms range on upgrade.
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
        write(settings, revision.incrementAndGet());
    }

    public java.util.concurrent.CompletableFuture<Void> saveAsync(TrackerSettings settings, java.util.concurrent.Executor executor) {
        // Snapshot on the client thread, write on the worker. Older queued snapshots cannot undo later UI changes.
        var copy = GSON.fromJson(GSON.toJson(settings), TrackerSettings.class);
        long version = revision.incrementAndGet();
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { write(copy, version); }
            catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }, executor);
    }

    private synchronized void write(TrackerSettings settings, long version) throws IOException {
        if (version != revision.get()) return;
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
