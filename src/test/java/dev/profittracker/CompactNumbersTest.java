package dev.profittracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactNumbersTest {
    @Test void abbreviatesAndTruncatesWithoutInflatingCounters() {
        assertEquals("999", CompactNumbers.format(999.99));
        assertEquals("1k", CompactNumbers.format(1_000));
        assertEquals("1.23k", CompactNumbers.format(1_239));
        assertEquals("12.34m", CompactNumbers.format(12_349_999));
        assertEquals("999.99k", CompactNumbers.format(999_999));
        assertEquals("1b", CompactNumbers.format(1_000_000_000));
        assertEquals("--", CompactNumbers.format(Double.NaN));
    }
    @Test void durationsDoNotWasteAnHourColumnOnShortSessions() {
        assertEquals("0:00", CompactNumbers.duration(0));
        assertEquals("3:04", CompactNumbers.duration(184_000));
        assertEquals("1:01:01", CompactNumbers.duration(3_661_000));
    }
    @Test void hudSessionTimeUsesOnlyWholeHoursAndMinutes() {
        assertEquals("0M",CompactNumbers.shortTime(59_999));
        assertEquals("1M",CompactNumbers.shortTime(60_999));
        assertEquals("1H 1M",CompactNumbers.shortTime(3_661_000));
        assertEquals("2H",CompactNumbers.shortTime(7_200_000));
    }
}
