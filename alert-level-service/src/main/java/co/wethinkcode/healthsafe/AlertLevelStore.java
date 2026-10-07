package co.wethinkcode.healthsafe;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the hospital's current Emergency Status.
 * 0 = normal ... 8 = full Code Blue.
 *
 * Many request threads read and write this at once, so the value lives in an
 * AtomicInteger rather than a plain int field.
 */
public final class AlertLevelStore {

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 8;

    private final AtomicInteger level = new AtomicInteger(MIN_LEVEL);

    public int get() {
        return level.get();
    }

    // Sets the level, or throws IllegalArgumentException if it is outside 0-8.
    public void set(int newLevel) {
        if (newLevel < MIN_LEVEL || newLevel > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "level must be between " + MIN_LEVEL + " and " + MAX_LEVEL + " but was " + newLevel);
        }
        level.set(newLevel);
    }
}