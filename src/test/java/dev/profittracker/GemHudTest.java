package dev.profittracker;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GemHudTest {
    private GemPrices prices() {
        return new GemPrices(Map.of(Gemstone.JADE, new GemPrices.Sale(GemPrices.Route.FINE_NPC, 19_200, 80)), 2_000_000);
    }
    @Test void gameplayHudIsFourLinesWithCompactCoinsWarmupAndSaleRoute() {
        var session = new GemSession(); session.start(0);
        var settings = new GemSettings();
        session.tick(0, true, false, false);
        session.tick(6_000, true, true, false);
        session.pristine(new GemMessages.Pristine(Gemstone.JADE, 10_000), 6_000);
        var warmup = GemHud.lines(session, prices(), settings);
        assertEquals(4, warmup.size());
        assertEquals("--/h | 2.4m total", warmup.get(1));
        assertTrue(warmup.get(3).contains("average ready in"));
        session.tick(180_000, true, false, false);
        var ready = GemHud.lines(session, prices(), settings);
        assertEquals("48m/h | 2.4m total", ready.get(1));
        assertEquals("Fine NPC | 3m avg", ready.get(3));
        assertTrue(ready.stream().noneMatch(line -> line.contains("TgoodUtils") || line.contains("proc")));
    }
    @Test void missingPricesRemainUnavailableAndPausedStateIsVisible() {
        var session = new GemSession(); session.start(0);
        session.pristine(new GemMessages.Pristine(Gemstone.JADE, 20), 0);
        session.pause(100);
        var lines = GemHud.lines(session, new GemPrices(Map.of(), Double.NaN), new GemSettings());
        assertTrue(lines.get(0).contains("Paused"));
        assertTrue(lines.get(1).contains("-- total"));
        assertEquals("Waiting for prices", lines.get(3));
    }
}
