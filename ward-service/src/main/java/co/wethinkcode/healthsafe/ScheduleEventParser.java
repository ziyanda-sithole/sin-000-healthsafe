package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads and checks the JSON text of a message from staffing-events-topic.
 * The format is documented in common/README.md.

 * Three outcomes:
 *  - a valid ScheduleUpdated event            -> Optional with the event
 *  - an event type we do not handle            -> empty (not an error: other kinds may share the topic)
 *  - broken JSON or a ScheduleUpdated that is missing or has impossible fields -> IOException
 */
public final class ScheduleEventParser {

    private static final List<String> REQUIRED_FIELDS =
            List.of("wardId", "alertLevel", "status", "doctorsOnCall", "issuedAtMillis");

    // Be liberal in what we accept: ignore fields added by a newer staffing-service.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ScheduleEventParser() {
    }

    public static Optional<ScheduleUpdatedEvent> parse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode type = root == null ? null : root.get("type");
        if (type == null || !ScheduleUpdatedEvent.TYPE.equals(type.asText())) {
            return Optional.empty();
        }

        // Without this a missing number would silently become 0 (zero doctors, level 0).
        for (String field : REQUIRED_FIELDS) {
            if (!root.hasNonNull(field)) {
                throw new IOException("ScheduleUpdated event is missing '" + field + "'");
            }
        }

        ScheduleUpdatedEvent event = MAPPER.treeToValue(root, ScheduleUpdatedEvent.class);
        if (event.wardId().isBlank()) {
            throw new IOException("ScheduleUpdated event has a blank wardId");
        }
        if (event.alertLevel() < 0 || event.alertLevel() > 8) {
            throw new IOException("ScheduleUpdated event has an impossible alertLevel: " + event.alertLevel());
        }
        if (event.doctorsOnCall() < 0) {
            throw new IOException("ScheduleUpdated event has negative doctorsOnCall: " + event.doctorsOnCall());
        }
        return Optional.of(event);
    }
}