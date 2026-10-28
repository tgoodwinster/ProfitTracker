package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GemMessagesTest {
    @Test void parsesRealPristineFormattingAndIcons() {
        var jade = GemMessages.pristine("§d§lPRISTINE! §r§fYou found §r§a☘ Flawed Jade Gemstone §r§8x20§r§f!").orElseThrow();
        assertEquals(Gemstone.JADE, jade.gemstone());
        assertEquals(20, jade.flawed());
        var amethyst = GemMessages.pristine("§d§lPRISTINE! §r§fYou found §r§a❈ Flawed Amethyst Gemstone §r§8x16§r§f!").orElseThrow();
        assertEquals(Gemstone.AMETHYST, amethyst.gemstone());
        assertEquals(16, amethyst.flawed());
        var privateIcon = GemMessages.pristine("PRISTINE! You found \uE053 Flawed Jade Gemstone x20!").orElseThrow();
        assertEquals(Gemstone.JADE, privateIcon.gemstone());
        assertEquals(20, privateIcon.flawed());
    }

    @Test void acceptsKnownGemTypesAndStrictCommaQuantities() {
        for (var gemstone : Gemstone.values()) {
            var gain = GemMessages.pristine("PRISTINE! You found \uE053 Flawed " + gemstone.name() + " Gemstone x1,024!").orElseThrow();
            assertEquals(gemstone, gain.gemstone());
            assertEquals(1_024, gain.flawed());
            assertEquals(gain, GemMessages.pristine("PRISTINE! You found a Flawed "
                    + gemstone.name() + " Gemstone (x1,024)!").orElseThrow());
        }
    }

    @Test void rejectsPlayerQuotesOtherTiersSackTransfersAndGuessedAmounts() {
        for (String text : new String[]{
                "[MVP+] Player: PRISTINE! You found Flawed Ruby Gemstone x20!",
                "[Sacks] +20 Flawed Ruby Gemstone", "You withdrew 20 Flawed Ruby Gemstone",
                "PRISTINE! You found Rough Ruby Gemstone x20!", "PRISTINE! You found Fine Ruby Gemstone x20!",
                "PRISTINE! You found Flawed Unknown Gemstone x20!", "PRISTINE! You found Flawed Ruby Gemstone!",
                "PRISTINE! You found Flawed Ruby Gemstone x0!", "PRISTINE! You found Flawed Ruby Gemstone x-2!",
                "PRISTINE! You found Flawed Ruby Gemstone x1,,024!", "PRISTINE! You found Flawed Ruby Gemstone x1,01!",
                "PRISTINE! You found Flawed Ruby Gemstone x999999999999999999!"}) {
            assertTrue(GemMessages.pristine(text).isEmpty(), text);
        }
    }

    @Test void glossyReadsOneAndTwoWithoutAssumingOneDropPerMessage() {
        String format = GemMessages.DEFAULT_GLOSSY_FORMAT;
        assertEquals(1, GemMessages.glossy("[SkyOcean] Sack Notification | Glossy Gemstone x1", format).orElseThrow());
        assertEquals(2, GemMessages.glossy("§b[SkyOcean] §7Sack Notification | §fGlossy Gemstone §8x2", format).orElseThrow());
        assertEquals(2, GemMessages.glossy("[SKYOCEAN] SACK NOTIFICATION |  Glossy Gemstone x2", format).orElseThrow());
        assertEquals(1_024, GemMessages.glossy("[SkyOcean] Sack Notification | Glossy Gemstone x1,024", format).orElseThrow());
    }

    @Test void glossyRejectsQuotedTransferredAndInvalidSkyOceanNotifications() {
        for (String text : new String[]{
                "[SkyOcean] Sack Notification | Glossy Gemstone x-2",
                "[SkyOcean] Sack Notification | Glossy Gemstone x0",
                "[SkyOcean] Sack Notification | Glossy Gemstone x1,01",
                "[SkyOcean] Sack Notification | Glossy Gemstone x2 (total: 5)",
                "[Player] [SkyOcean] Sack Notification | Glossy Gemstone x2",
                "Removed [SkyOcean] Sack Notification | Glossy Gemstone x2",
                "[SkyOcean] Sack Notification | Removed Glossy Gemstone x2",
                "[SkyOcean] Sack Notification | Glossy Gemstone x999999999999999"}) {
            assertTrue(GemMessages.glossy(text, GemMessages.DEFAULT_GLOSSY_FORMAT).isEmpty(), text);
        }
    }

    @Test void realSkyOceanExampleLearnsTheQuantityInsteadOfFixingItAtOne() {
        String format = GemMessages.formatFromExample("[SkyOcean] Sack Notification | Glossy Gemstone x1");
        assertEquals(GemMessages.DEFAULT_GLOSSY_FORMAT, format);
        assertEquals(2, GemMessages.glossy("[SkyOcean] Sack Notification | Glossy Gemstone x2", format).orElseThrow());
    }

    @Test void skyOceanNotificationsCanContainOtherSelectedItemsAndCommaQuantities() {
        String format = GemMessages.DEFAULT_GLOSSY_FORMAT;
        assertEquals(2, GemMessages.glossy("[SkyOcean] Sack Notification | Glossy Gemstone x2, Enchanted Glacite x8", format).orElseThrow());
        assertEquals(1_024, GemMessages.glossy("[SkyOcean] Sack Notification | Enchanted Glacite x8, Glossy Gemstone x1,024, Rough Jade Gemstone x3,749", format).orElseThrow());
        assertEquals(1, GemMessages.glossy("§b[SkyOcean] §7Sack Notification | Coal x40, §fGlossy Gemstone §8x1", format).orElseThrow());
        assertTrue(GemMessages.glossy("[SkyOcean] Sack Notification | Coal x40, Rough Jade Gemstone x3,749", format).isEmpty());
    }

    @Test void skyOceanGroupedMessagesRejectDuplicateGlossyAndMalformedEntries() {
        for (String text : new String[]{
                "[SkyOcean] Sack Notification | Glossy Gemstone x1, Glossy Gemstone x2",
                "[SkyOcean] Sack Notification | Glossy Gemstone x1, 024",
                "[SkyOcean] Sack Notification | Glossy Gemstone x2, Coal x-3",
                "[SkyOcean] Sack Notification | Glossy Gemstone x2, Coal",
                "[SkyOcean] Sack Notification | Glossy Gemstone x2, ",
                "Player: [SkyOcean] Sack Notification | Coal x40, Glossy Gemstone x2"}) {
            assertTrue(GemMessages.glossy(text, GemMessages.DEFAULT_GLOSSY_FORMAT).isEmpty(), text);
        }
    }

    @Test void pastedGroupedExampleCannotLearnAnotherItemsQuantityAsGlossy() {
        assertEquals(GemMessages.DEFAULT_GLOSSY_FORMAT,
                GemMessages.formatFromExample("[SkyOcean] Sack Notification | Glossy Gemstone x2, Coal x40"));
        assertEquals(GemMessages.DEFAULT_GLOSSY_FORMAT,
                GemMessages.formatFromExample("[SkyOcean] Sack Notification | Coal x40, Glossy Gemstone x2"));
        assertEquals("[OtherMod] Glossy Gemstone x{amount}, Coal x40",
                GemMessages.formatFromExample("[OtherMod] Glossy Gemstone x2, Coal x40"));
    }

    @Test void customizedLiteralFormatWorksAndExampleLearnsQuantity() {
        String example = "[OtherMod] Sac Notification - Glossy Gemstone x2!";
        String format = GemMessages.formatFromExample(example);
        assertEquals("[OtherMod] Sac Notification - Glossy Gemstone x{amount}!", format);
        assertTrue(GemMessages.validGlossyFormat(format));
        assertEquals(1, GemMessages.glossy(example.replace("x2", "x1"), format).orElseThrow());
        assertEquals(2, GemMessages.glossy(example, format).orElseThrow());
        assertTrue(GemMessages.glossy("OtherMod Sac Notification - Glossy Gemstone x2!", format).isEmpty());
    }

    @Test void invalidGlossyFormatsAndExcessiveCountsAreNotAccepted() {
        assertFalse(GemMessages.validGlossyFormat("anything {amount}"));
        assertFalse(GemMessages.validGlossyFormat("Glossy Gemstone x{amount} {amount}"));
        assertFalse(GemMessages.validGlossyFormat("Removed Glossy Gemstone x{amount}"));
        assertFalse(GemMessages.validGlossyFormat("Withdraw Glossy Gemstone x{amount}"));
        assertTrue(GemMessages.glossy("[SkyOcean] Sack Notification | Glossy Gemstone x999999999999999", GemMessages.DEFAULT_GLOSSY_FORMAT).isEmpty());
    }
}
