package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The alerts this service has processed, remembered by alertId.

 * A queue promises AT LEAST ONCE delivery: if this service dies after handling an alert but
 * before the broker hears about it, the alert comes again. Remembering ids makes handling
 * idempotent, so a duplicate is recognised instead of being raised twice.

 * Kept in memory and never trimmed, so it is lost on restart and grows without limit.
 */
public final class AlertLog {

    private final Map<String, EquipmentFailureAlert> byId = new LinkedHashMap<>();

    // Records the alert. Returns true if it is new, false if this alertId was already handled.
    public synchronized boolean record(EquipmentFailureAlert alert) {
        return byId.putIfAbsent(alert.alertId(), alert) == null;
    }

    public synchronized List<EquipmentFailureAlert> newestFirst() {
        List<EquipmentFailureAlert> alerts = new ArrayList<>(byId.values());
        Collections.reverse(alerts);
        return alerts;
    }
}