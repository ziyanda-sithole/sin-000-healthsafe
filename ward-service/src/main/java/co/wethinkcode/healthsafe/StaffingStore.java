package co.wethinkcode.healthsafe;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the most recent staffing event received for each ward.

 * Events arrive on a broker thread while HTTP requests read on others, hence the
 * ConcurrentHashMap. Rule: NEWEST WINS, judged by issuedAtMillis, so an old event that
 * arrives late can never overwrite a newer one. On a tie the later arrival wins.
 */
public final class StaffingStore {

    private final Map<String, ScheduleUpdatedEvent> latestByWard = new ConcurrentHashMap<>();

    public void accept(ScheduleUpdatedEvent event) {
        String key = key(event.wardId());
        if (key.isEmpty()) {
            return; // cannot file an event that names no ward
        }
        // merge() runs the comparison atomically, so two events racing for the same ward
        // cannot both "win".
        latestByWard.merge(key, event, (current, incoming) ->
                incoming.issuedAtMillis() >= current.issuedAtMillis() ? incoming : current);
    }

    // Latest event for the ward; the id ignores case and padding. Empty if none received yet.
    public Optional<ScheduleUpdatedEvent> find(String wardId) {
        return Optional.ofNullable(latestByWard.get(key(wardId)));
    }

    private static String key(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }
}