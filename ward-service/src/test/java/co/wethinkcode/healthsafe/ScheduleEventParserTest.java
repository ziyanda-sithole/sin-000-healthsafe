package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScheduleEventParserTest {

    private static final String VALID = """
            {"type":"ScheduleUpdated","wardId":"W-05","alertLevel":8,
             "status":"CODE_BLUE","doctorsOnCall":9,"issuedAtMillis":1791360000000}""";

    @Test
    void parsesAValidEvent() throws IOException {
        Optional<ScheduleUpdatedEvent> event = ScheduleEventParser.parse(VALID);

        assertEquals(
                new ScheduleUpdatedEvent("ScheduleUpdated", "W-05", 8, "CODE_BLUE", 9, 1791360000000L),
                event.orElseThrow());
    }

    @Test
    void ignoresFieldsItDoesNotKnow() throws IOException {
        String json = VALID.replace("\"type\"", "\"futureField\":true,\"type\"");

        assertTrue(ScheduleEventParser.parse(json).isPresent());
    }

    @Test
    void aDifferentEventTypeIsIgnoredNotAnError() throws IOException {
        String json = VALID.replace("ScheduleUpdated", "SomethingElse");

        assertTrue(ScheduleEventParser.parse(json).isEmpty());
    }

    @Test
    void jsonWithNoTypeIsIgnored() throws IOException {
        assertTrue(ScheduleEventParser.parse("{\"wardId\":\"W-05\"}").isEmpty());
        assertTrue(ScheduleEventParser.parse("[]").isEmpty());
    }

    @Test
    void aMissingFieldIsRejectedNotTurnedIntoZero() {
        String noDoctors = VALID.replace("\"doctorsOnCall\":9,", "");

        IOException error = assertThrows(IOException.class, () -> ScheduleEventParser.parse(noDoctors));

        assertTrue(error.getMessage().contains("doctorsOnCall"), error.getMessage());
    }

    @Test
    void impossibleValuesAreRejected() {
        assertThrows(IOException.class,
                () -> ScheduleEventParser.parse(VALID.replace("\"alertLevel\":8", "\"alertLevel\":9")));
        assertThrows(IOException.class,
                () -> ScheduleEventParser.parse(VALID.replace("\"doctorsOnCall\":9", "\"doctorsOnCall\":-1")));
        assertThrows(IOException.class,
                () -> ScheduleEventParser.parse(VALID.replace("\"W-05\"", "\"  \"")));
    }

    @Test
    void brokenJsonIsRejected() {
        assertThrows(IOException.class, () -> ScheduleEventParser.parse("not json"));
    }
}