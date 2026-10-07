package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScheduleUpdatedEventTest {

    @Test
    void buildsAnEventFromASchedule() {
        OnCallSchedule schedule = new OnCallSchedule("W-05", 8, "CODE_BLUE", 9);

        ScheduleUpdatedEvent event = ScheduleUpdatedEvent.from(schedule, 1791360000000L);

        assertEquals(
                new ScheduleUpdatedEvent("ScheduleUpdated", "W-05", 8, "CODE_BLUE", 9, 1791360000000L),
                event);
    }

    @Test
    void typeIsAlwaysScheduleUpdated() {
        ScheduleUpdatedEvent event =
                ScheduleUpdatedEvent.from(new OnCallSchedule("W-01", 0, "NORMAL", 1), 1L);

        assertEquals(ScheduleUpdatedEvent.TYPE, event.type());
    }
}