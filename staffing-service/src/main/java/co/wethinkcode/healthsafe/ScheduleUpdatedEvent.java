package co.wethinkcode.healthsafe;

/**
 * Message broadcast on staffing-events-topic when staffing-service issues a schedule.

 * It carries the FULL current state for the ward (not "doctors +2"), so a subscriber that
 * missed an earlier event is repaired by the next one.

 * issuedAtMillis is epoch milliseconds: a plain number that compares correctly and needs
 * no date module in Jackson. Subscribers use it to keep only the newest event per ward.
 */
public record ScheduleUpdatedEvent(
        String type,
        String wardId,
        int alertLevel,
        String status,
        int doctorsOnCall,
        long issuedAtMillis) {

    // Lets more event kinds share the topic later; consumers ignore types they do not know.
    public static final String TYPE = "ScheduleUpdated";

    public static ScheduleUpdatedEvent from(OnCallSchedule schedule, long issuedAtMillis) {
        return new ScheduleUpdatedEvent(
                TYPE,
                schedule.wardId(),
                schedule.alertLevel(),
                schedule.status(),
                schedule.doctorsOnCall(),
                issuedAtMillis);
    }
}