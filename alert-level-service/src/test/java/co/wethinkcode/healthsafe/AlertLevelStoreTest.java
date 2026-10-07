package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AlertLevelStoreTest {

    @Test
    void startsAtZero() {
        assertEquals(0, new AlertLevelStore().get());
    }

    @Test
    void acceptsEveryLevelFromZeroToEight() {
        AlertLevelStore store = new AlertLevelStore();
        for (int level = 0; level <= 8; level++) {
            store.set(level);
            assertEquals(level, store.get());
        }
    }

    @Test
    void rejectsLevelsOutsideTheRange() {
        AlertLevelStore store = new AlertLevelStore();

        assertThrows(IllegalArgumentException.class, () -> store.set(-1));
        assertThrows(IllegalArgumentException.class, () -> store.set(9));
    }

    @Test
    void aRejectedValueLeavesTheCurrentLevelUnchanged() {
        AlertLevelStore store = new AlertLevelStore();
        store.set(5);

        assertThrows(IllegalArgumentException.class, () -> store.set(99));

        assertEquals(5, store.get());
    }
}