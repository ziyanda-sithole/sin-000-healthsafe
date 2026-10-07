package co.wethinkcode.healthsafe;

/**
 * The on-call schedule for one ward at one moment.
 *
 * @param wardId        normalised ward id, e.g. "W-05"
 * @param alertLevel    hospital Emergency Status used to size the schedule (0-8)
 * @param status        human-readable band for that level, e.g. "ELEVATED"
 * @param doctorsOnCall how many doctors must be on call for this ward
 */
public record OnCallSchedule(String wardId, int alertLevel, String status, int doctorsOnCall) {
}