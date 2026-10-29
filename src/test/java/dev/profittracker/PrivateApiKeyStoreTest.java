package dev.profittracker;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PrivateApiKeyStoreTest {
    @TempDir Path folder;
    @Test void keyIsSeparateFromOrdinarySettingsAndCanBeRemoved() throws Exception {
        String fake = "test" + "-".repeat(20);
        var path = folder.resolve("tgoodutils-private.json"); var store = new PrivateApiKeyStore(path);
        assertEquals("", store.load()); store.save(fake); assertEquals(fake, store.load());
        var regular = folder.resolve("profittracker.json"); new SettingsStore(regular).save(new TrackerSettings());
        assertFalse(Files.readString(regular).contains(fake));
        try { assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(path))); }
        catch (UnsupportedOperationException ignored) { }
        store.save(""); assertFalse(Files.exists(path));
    }
    @Test void invalidInputAndCorruptFilesDoNotEchoKeyMaterial() throws Exception {
        var path = folder.resolve("tgoodutils-private.json"); var store = new PrivateApiKeyStore(path);
        String bad = "sensitive-example with spaces";
        var error = assertThrows(java.io.IOException.class, () -> store.save(bad)); assertFalse(error.getMessage().contains(bad));
        Files.writeString(path,"not a private key file");
        assertThrows(java.io.IOException.class, store::load);
    }
}
