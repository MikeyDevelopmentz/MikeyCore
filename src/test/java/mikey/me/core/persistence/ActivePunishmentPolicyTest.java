package mikey.me.core.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivePunishmentPolicyTest {

    private static final long START = 1_700_000_000_000L;

    @Test
    void clearedPunishmentIsNeverActive() {
        assertFalse(ActivePunishmentPolicy.isActive(false, 0L, START, START));
        assertFalse(ActivePunishmentPolicy.isActive(false, 60L, START, START));
        assertFalse(ActivePunishmentPolicy.isActive(false, 60L, START, START + 59_999L));
        assertFalse(ActivePunishmentPolicy.isActive(false, 60L, START + 5_000L, START));
        assertFalse(ActivePunishmentPolicy.isActive(false, 60L, START - 500_000L, START + 500_000L));
    }

    @Test
    void permanentPunishmentStaysActiveRegardlessOfAge() {
        assertTrue(ActivePunishmentPolicy.isActive(true, 0L, START, START));
        assertTrue(ActivePunishmentPolicy.isActive(true, 0L, START, START + 86_400_000L));
        assertTrue(ActivePunishmentPolicy.isActive(true, 0L, START, Long.MAX_VALUE));
        assertTrue(ActivePunishmentPolicy.isActive(true, 0L, Long.MIN_VALUE, START));
    }

    @Test
    void nonPositiveDurationIsTreatedAsPermanent() {
        assertTrue(ActivePunishmentPolicy.isActive(true, -1L, START, START + 3_600_000L));
        assertTrue(ActivePunishmentPolicy.isActive(true, -3_600L, START, START));
        assertTrue(ActivePunishmentPolicy.isActive(true, Long.MIN_VALUE, START, START));
    }

    @Test
    void futureStartIsStillActive() {
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START + 1L, START));
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START + 60_000L, START));
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, Long.MAX_VALUE, START));
    }

    @Test
    void activeExactlyAtStartInstant() {
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START, START));
        assertTrue(ActivePunishmentPolicy.isActive(true, 1L, START, START));
    }

    @Test
    void activeThroughoutWindow() {
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START, START + 1L));
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START, START + 30_000L));
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START, START + 59_999L));
        assertTrue(ActivePunishmentPolicy.isActive(true, 3_600L, START, START + 3_599_999L));
    }

    @Test
    void inactiveExactlyAtExpiryInstant() {
        assertFalse(ActivePunishmentPolicy.isActive(true, 60L, START, START + 60_000L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 1L, START, START + 1_000L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 3_600L, START, START + 3_600_000L));
    }

    @Test
    void inactiveAfterExpiry() {
        assertFalse(ActivePunishmentPolicy.isActive(true, 60L, START, START + 60_001L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 1L, START, START + 5_000L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 3_600L, START, START + 604_800_000L));
    }

    @Test
    void windowEndsExactlyAtStartPlusDurationMillis() {
        assertTrue(ActivePunishmentPolicy.isActive(true, 1L, 0L, 0L));
        assertTrue(ActivePunishmentPolicy.isActive(true, 1L, 0L, 999L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 1L, 0L, 1_000L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 1L, 0L, 1_999L));
        assertTrue(ActivePunishmentPolicy.isActive(true, 2L, 0L, 1_999L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 2L, 0L, 2_000L));
        assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START, START + 59_999L));
        assertFalse(ActivePunishmentPolicy.isActive(true, 60L, START, START + 60_000L));
    }

    @Test
    void flooredElapsedSecondsMatchMillisecondWindow() {
        for (long duration = 1L; duration <= 5L; duration++) {
            long boundary = duration * 1_000L;
            assertTrue(ActivePunishmentPolicy.isActive(true, duration, 0L, boundary - 1L));
            assertFalse(ActivePunishmentPolicy.isActive(true, duration, 0L, boundary));
        }
    }

    @Test
    void matchesStoredPunishmentQuerySemantics() {
        long[] starts = {0L, 1L, 1_000L, START};
        long[] durations = {0L, 1L, 2L, 59L, 60L, 3_600L, 86_400L};
        for (long start : starts) {
            for (long duration : durations) {
                long span = Math.max(2_000L, duration * 1_000L);
                for (long now = start - 2_000L; now <= start + span + 2_000L; now += 250L) {
                    boolean expected = duration <= 0L || start + duration * 1_000L > now;
                    boolean actual = ActivePunishmentPolicy.isActive(true, duration, start, now);
                    if (expected != actual) {
                        throw new AssertionError("mismatch for start=" + start
                                + " duration=" + duration + " now=" + now);
                    }
                }
            }
        }
    }

    @Test
    void isStatelessAndDeterministic() {
        for (int i = 0; i < 5; i++) {
            assertTrue(ActivePunishmentPolicy.isActive(true, 60L, START, START + 1_000L));
            assertFalse(ActivePunishmentPolicy.isActive(true, 60L, START, START + 90_000L));
        }
    }
}
