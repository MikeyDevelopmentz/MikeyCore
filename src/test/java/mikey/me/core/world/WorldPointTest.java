package mikey.me.core.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPointTest {

    private final WorldPoint point = new WorldPoint("world", 0, 0, 0);

    @Test
    void showsInsideAndHidesPastTheBuffer() {
        assertTrue(point.within("world", 40, 0, 0, 48, 56, false));
        assertTrue(point.within("world", 48, 0, 0, 48, 56, false));
        assertFalse(point.within("world", 50, 0, 0, 48, 56, false));
        assertTrue(point.within("world", 50, 0, 0, 48, 56, true));
        assertTrue(point.within("world", 56, 0, 0, 48, 56, true));
        assertFalse(point.within("world", 60, 0, 0, 48, 56, true));
    }

    @Test
    void otherWorldIsNeverInside() {
        assertFalse(point.within("nether", 0, 0, 0, 48, 56, true));
        assertFalse(point.within(null, 0, 0, 0, 48, 56, true));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint(" ", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint("world", Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> point.within("world", 0, 0, 0, 0, 10, false));
        assertThrows(IllegalArgumentException.class, () -> point.within("world", 0, 0, 0, 20, 10, false));
    }

    // world mismatch gives +Infinity and the compare was plain <=, so infinite hide leaked across worlds
    @Test
    void infiniteHideDoesNotLeakAcrossWorlds() {
        assertFalse(point.within("nether", 0, 0, 0, 10, Double.POSITIVE_INFINITY, true));
        assertFalse(point.within("nether", 0, 0, 0, 10, Double.POSITIVE_INFINITY, false));
        assertTrue(point.within("world", 0, 0, 0, 10, Double.POSITIVE_INFINITY, true));
    }

    // NaN fails every compare, so both old guards let it through
    @Test
    void rejectsNaNRadii() {
        assertThrows(IllegalArgumentException.class,
                () -> point.within("world", 0, 0, 0, Double.NaN, 20, false));
        assertThrows(IllegalArgumentException.class,
                () -> point.within("world", 0, 0, 0, 10, Double.NaN, true));
    }
}
