package co.wethinkcode.healthsafe;

/**
 * Staffing update received from staffing-events-topic.
 * Mirrors the JSON documented in common/README.md ("Event format").

 * Deliberately a COPY of staffing-service's class: these are independent Maven projects, so the
 * two sides share the JSON format, not a Java class.
 */
public record ScheduleUpdatedEvent(
        String type,
        String wardId,
        int alertLevel,
        String status,
        int doctorsOnCall,
        long issuedAtMillis) {

    public static final String TYPE = "ScheduleUpdated";
}