package co.wethinkcode.healthsafe;

/**
 * A critical equipment failure queued for equipment-alert-service on equipment-failure-queue.
 * Mirrors the JSON that service reads. A COPY on purpose: independent Maven projects share the
 * JSON format, not a Java class.
 *
 * @param alertId          unique id made here, so the consumer can recognise a redelivered duplicate
 * @param wardId           canonical ward id, e.g. "W-05"
 * @param equipment        what failed, e.g. "ventilator"
 * @param details          optional free text; null when none was given
 * @param reportedAtMillis when this service accepted the report, epoch milliseconds
 */
public record EquipmentFailureAlert(
        String alertId,
        String wardId,
        String equipment,
        String details,
        long reportedAtMillis) {
}