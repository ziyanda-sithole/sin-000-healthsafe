package co.wethinkcode.healthsafe;

/**
 * A critical medical equipment failure reported by ward-service on equipment-failure-queue.

 * @param alertId         unique id made by the producer; lets this service recognise a
 *                        duplicate, because a queue guarantees "at least once", not "exactly once"
 * @param wardId          the ward the equipment is in, e.g. "W-05"
 * @param equipment       what failed, e.g. "ventilator"
 * @param details         optional free text; may be null
 * @param reportedAtMillis when ward-service accepted the report, epoch milliseconds
 */
public record EquipmentFailureAlert(
        String alertId,
        String wardId,
        String equipment,
        String details,
        long reportedAtMillis) {
}