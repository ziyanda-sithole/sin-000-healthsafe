package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StaffingPolicyTest {

    @Test
    void doctorsGrowWithTheLevelFromOneToNine() {
        assertEquals(1, StaffingPolicy.doctorsOnCall(0));
        assertEquals(5, StaffingPolicy.doctorsOnCall(4));
        assertEquals(9, StaffingPolicy.doctorsOnCall(8));
    }

    @Test
    void statusBandsChangeExactlyAtTheirBoundaries() {
        assertEquals("NORMAL", StaffingPolicy.status(0));
        assertEquals("NORMAL", StaffingPolicy.status(2));
        assertEquals("ELEVATED", StaffingPolicy.status(3));
        assertEquals("ELEVATED", StaffingPolicy.status(5));
        assertEquals("CRITICAL", StaffingPolicy.status(6));
        assertEquals("CRITICAL", StaffingPolicy.status(7));
        assertEquals("CODE_BLUE", StaffingPolicy.status(8));
    }

    @Test
    void scheduleCombinesWardLevelStatusAndDoctors() {
        OnCallSchedule schedule = StaffingPolicy.scheduleFor("W-05", 8);

        assertEquals(new OnCallSchedule("W-05", 8, "CODE_BLUE", 9), schedule);
    }

    @Test
    void levelsOutsideZeroToEightAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> StaffingPolicy.doctorsOnCall(-1));
        assertThrows(IllegalArgumentException.class, () -> StaffingPolicy.doctorsOnCall(9));
        assertThrows(IllegalArgumentException.class, () -> StaffingPolicy.status(-1));
        assertThrows(IllegalArgumentException.class, () -> StaffingPolicy.status(9));
    }
}