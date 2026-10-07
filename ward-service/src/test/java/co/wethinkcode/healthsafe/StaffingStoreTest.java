package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaffingStoreTest {

    private static ScheduleUpdatedEvent event(String ward, int level, int doctors, long issuedAt) {
        return new ScheduleUpdatedEvent("ScheduleUpdated", ward, level, "STATUS", doctors, issuedAt);
    }

    private final StaffingStore store = new StaffingStore();

    @Test
    void hasNothingBeforeAnyEventArrives() {
        assertTrue(store.find("W-05").isEmpty());
    }

    @Test
    void aNewerEventReplacesAnOlderOne() {
        store.accept(event("W-05", 3, 4, 1000));
        store.accept(event("W-05", 8, 9, 2000));

        assertEquals(9, store.find("W-05").orElseThrow().doctorsOnCall());
    }

    @Test
    void anOldEventArrivingLateIsIgnored() {
        store.accept(event("W-05", 8, 9, 2000));
        store.accept(event("W-05", 1, 2, 1500)); // issued earlier, delivered later

        assertEquals(9, store.find("W-05").orElseThrow().doctorsOnCall());
    }

    @Test
    void onATieTheLaterArrivalWins() {
        store.accept(event("W-05", 8, 9, 2000));
        store.accept(event("W-05", 5, 6, 2000));

        assertEquals(6, store.find("W-05").orElseThrow().doctorsOnCall());
    }

    @Test
    void wardIdsIgnoreCaseAndPaddingOnBothSides() {
        store.accept(event("w-07", 2, 3, 10));

        assertEquals(3, store.find(" W-07 ").orElseThrow().doctorsOnCall());
    }

    @Test
    void wardsAreKeptSeparately() {
        store.accept(event("W-01", 0, 1, 10));
        store.accept(event("W-02", 8, 9, 5)); // older timestamp, but a different ward

        assertEquals(1, store.find("W-01").orElseThrow().doctorsOnCall());
        assertEquals(9, store.find("W-02").orElseThrow().doctorsOnCall());
    }

    @Test
    void eventsThatNameNoWardAreIgnored() {
        store.accept(event("  ", 2, 3, 10));
        store.accept(event(null, 2, 3, 10));

        assertTrue(store.find(null).isEmpty());
        assertTrue(store.find("").isEmpty());
    }
}