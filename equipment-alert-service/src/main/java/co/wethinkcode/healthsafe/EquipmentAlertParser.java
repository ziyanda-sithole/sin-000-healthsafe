package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Reads and checks the JSON text of a message from equipment-failure-queue.

 * Unlike the topic, this queue carries only alerts, so anything that is not a valid alert is an
 * error (IOException) rather than something to skip: the consumer must not quietly lose a
 * message that might be a real emergency.
 */
public final class EquipmentAlertParser {

    private static final List<String> REQUIRED_FIELDS =
            List.of("alertId", "wardId", "equipment", "reportedAtMillis");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EquipmentAlertParser() {
    }

    public static EquipmentFailureAlert parse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IOException("alert is not a JSON object");
        }
        // Without this a missing number would silently become 0.
        for (String field : REQUIRED_FIELDS) {
            if (!root.hasNonNull(field)) {
                throw new IOException("alert is missing '" + field + "'");
            }
        }
        EquipmentFailureAlert alert = MAPPER.treeToValue(root, EquipmentFailureAlert.class);
        if (alert.alertId().isBlank() || alert.wardId().isBlank() || alert.equipment().isBlank()) {
            throw new IOException("alert has a blank alertId, wardId or equipment");
        }
        return alert;
    }
}