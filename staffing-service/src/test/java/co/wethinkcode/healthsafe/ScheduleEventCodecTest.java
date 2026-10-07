package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ScheduleEventCodecTest {

    @Test
    void producesTheDocumentedJsonFormat() throws Exception {
        ScheduleUpdatedEvent event =
                new ScheduleUpdatedEvent("ScheduleUpdated", "W-05", 8, "CODE_BLUE", 9, 1791360000000L);

        JsonNode json = new ObjectMapper().readTree(ScheduleEventCodec.toJson(event));

        assertEquals("ScheduleUpdated", json.get("type").asText());
        assertEquals("W-05", json.get("wardId").asText());
        assertEquals(8, json.get("alertLevel").asInt());
        assertEquals("CODE_BLUE", json.get("status").asText());
        assertEquals(9, json.get("doctorsOnCall").asInt());
        assertEquals(1791360000000L, json.get("issuedAtMillis").asLong());
    }

    @Test
    void containsExactlyTheDocumentedFields() throws Exception {
        ScheduleUpdatedEvent event =
                ScheduleUpdatedEvent.from(new OnCallSchedule("W-01", 0, "NORMAL", 1), 1L);

        JsonNode json = new ObjectMapper().readTree(ScheduleEventCodec.toJson(event));

        // a stray extra field (say, a static constant leaking in) would change this count
        assertEquals(6, json.size());
    }
}