package co.wethinkcode.healthsafe;

/**
 * What GET /wards/{id}/staffing returns: the latest staffing update received for a ward.

 * Deliberately not the topic's event itself. The event is a message format shared with
 * staffing-service; this is ward-service's own HTTP contract, so the two can change
 * independently and the message "type" envelope does not leak into the REST API.
 */
public record WardStaffing(
        String wardId,
        int alertLevel,
        String status,
        int doctorsOnCall,
        long issuedAtMillis) {

    public static WardStaffing from(ScheduleUpdatedEvent event) {
        return new WardStaffing(
                event.wardId(),
                event.alertLevel(),
                event.status(),
                event.doctorsOnCall(),
                event.issuedAtMillis());
    }
}