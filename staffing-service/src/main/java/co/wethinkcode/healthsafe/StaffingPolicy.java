package co.wethinkcode.healthsafe;

/**
 * The business rule: how many doctors a ward needs for a given Emergency Status.

 * doctors on call = 1 + alert level      (level 0 -> 1 doctor, level 8 -> 9 doctors)
 * Kept in its own class so the rule can change without touching the HTTP code.
 */
public final class StaffingPolicy {

    static final int MIN_LEVEL = 0;
    static final int MAX_LEVEL = 8;
    static final int BASE_DOCTORS = 1;

    private StaffingPolicy() {
    }

    public static int doctorsOnCall(int level) {
        requireValid(level);
        return BASE_DOCTORS + level;
    }

    // Names the band a level falls in: 0-2 NORMAL, 3-5 ELEVATED, 6-7 CRITICAL, 8 CODE_BLUE.
    public static String status(int level) {
        requireValid(level);
        if (level <= 2) {
            return "NORMAL";
        }
        if (level <= 5) {
            return "ELEVATED";
        }
        if (level <= 7) {
            return "CRITICAL";
        }
        return "CODE_BLUE";
    }

    public static OnCallSchedule scheduleFor(String wardId, int level) {
        return new OnCallSchedule(wardId, level, status(level), doctorsOnCall(level));
    }

    private static void requireValid(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "level must be between " + MIN_LEVEL + " and " + MAX_LEVEL + " but was " + level);
        }
    }
}