package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquipmentAlertParserTest {

    private static final String VALID = """
            {"alertId":"a-1","wardId":"W-05","equipment":"ventilator",
             "details":"no airflow","reportedAtMillis":1791360000000}""";

    @Test
    void parsesAValidAlert() throws IOException {
        assertEquals(
                new EquipmentFailureAlert("a-1", "W-05", "ventilator", "no airflow", 1791360000000L),
                EquipmentAlertParser.parse(VALID));
    }

    @Test
    void detailsAreOptional() throws IOException {
        ObjectNode node = (ObjectNode) new ObjectMapper().readTree(VALID);
        node.remove("details");

        assertNull(EquipmentAlertParser.parse(node.toString()).details());
    }

    @Test
    void ignoresFieldsItDoesNotKnow() throws IOException {
        String json = VALID.replace("{", "{\"futureField\":true,");

        assertEquals("a-1", EquipmentAlertParser.parse(json).alertId());
    }

    @Test
    void aMissingRequiredFieldIsRejected() throws IOException {
        for (String field : List.of("alertId", "wardId", "equipment", "reportedAtMillis")) {
            ObjectNode node = (ObjectNode) new ObjectMapper().readTree(VALID);
            node.remove(field);

            IOException error = assertThrows(IOException.class,
                    () -> EquipmentAlertParser.parse(node.toString()), "without " + field);
            assertEquals("alert is missing '" + field + "'", error.getMessage());
        }
    }

    @Test
    void blankValuesAreRejected() {
        assertThrows(IOException.class, () -> EquipmentAlertParser.parse(VALID.replace("\"ventilator\"", "\"  \"")));
        assertThrows(IOException.class, () -> EquipmentAlertParser.parse(VALID.replace("\"W-05\"", "\"\"")));
        assertThrows(IOException.class, () -> EquipmentAlertParser.parse(VALID.replace("\"a-1\"", "\" \"")));
    }

    @Test
    void anythingThatIsNotAnObjectIsRejected() {
        assertThrows(IOException.class, () -> EquipmentAlertParser.parse("[]"));
        assertThrows(IOException.class, () -> EquipmentAlertParser.parse("\"text\""));
    }

    @Test
    void brokenJsonIsRejected() {
        assertThrows(IOException.class, () -> EquipmentAlertParser.parse("not json"));
    }
}