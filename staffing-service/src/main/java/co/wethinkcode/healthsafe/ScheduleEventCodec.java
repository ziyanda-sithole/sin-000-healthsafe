package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts events to the JSON text sent over the broker.
 * The JSON format is documented in common/README.md; ward-service parses it with its own copy.
 */
public final class ScheduleEventCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScheduleEventCodec() {
    }

    public static String toJson(ScheduleUpdatedEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // A record of strings and numbers cannot fail to serialise, so reaching this
            // means a programming error, not bad input: unchecked is the right choice.
            throw new IllegalStateException("Could not serialise " + event, e);
        }
    }
}