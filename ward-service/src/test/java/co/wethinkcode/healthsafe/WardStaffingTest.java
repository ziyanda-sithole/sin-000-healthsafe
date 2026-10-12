package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WardStaffingTest {

    @Test
    void copiesTheStaffingFieldsAndDropsTheMessageEnvelope() {
        ScheduleUpdatedEvent event =
                new ScheduleUpdatedEvent("ScheduleUpdated", "W-05", 8, "CODE_BLUE", 9, 1791360000000L);

        assertEquals(new WardStaffing("W-05", 8, "CODE_BLUE", 9, 1791360000000L), WardStaffing.from(event));
    }
}