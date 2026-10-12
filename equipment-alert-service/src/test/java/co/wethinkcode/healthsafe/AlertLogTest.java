package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlertLogTest {

    private static EquipmentFailureAlert alert(String id, String equipment) {
        return new EquipmentFailureAlert(id, "W-05", equipment, null, 1L);
    }

    private final AlertLog log = new AlertLog();

    @Test
    void aNewAlertIsRecorded() {
        assertTrue(log.record(alert("a-1", "ventilator")));
    }

    @Test
    void aRepeatedAlertIdIsRecognisedAsADuplicate() {
        log.record(alert("a-1", "ventilator"));

        assertFalse(log.record(alert("a-1", "ventilator")));
    }

    @Test
    void listsNewestFirstWithoutDuplicates() {
        EquipmentFailureAlert first = alert("a-1", "ventilator");
        EquipmentFailureAlert second = alert("a-2", "monitor");
        log.record(first);
        log.record(second);
        log.record(first); // a duplicate delivery

        assertEquals(List.of(second, first), log.newestFirst());
    }
}