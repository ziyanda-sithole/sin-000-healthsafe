package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class WardCleanerTest {

    @Test
    void cleansTheReadmeExampleRow() {
        // the worked example from ingestion-service/README.md
        String[] raw = {"w-05", "east wing ", "PAEDIATRICS", "five"};

        Ward ward = WardCleaner.cleanRow(raw, 6);

        assertEquals("W-05", ward.wardId());
        assertEquals("East Wing", ward.wing());
        assertEquals("Paediatrics", ward.department());
        assertNull(ward.bedsAvailable());
        assertEquals(1, ward.notes().size());
        assertTrue(ward.notes().get(0).startsWith("row 6:"));
        assertTrue(ward.notes().get(0).contains("five"));
    }

    @Test
    void cleanRowHasNoNotesWhenEverythingIsFine() {
        Ward ward = WardCleaner.cleanRow(new String[] {"W-01", " East Wing ", "Cardiology", "3"}, 1);

        assertEquals(3, ward.bedsAvailable());
        assertTrue(ward.notes().isEmpty());
    }

    @Test
    void missingWingKeepsTheWardAndAddsANote() {
        Ward ward = WardCleaner.cleanRow(new String[] {"W-08", "", "Oncology", "4"}, 8);

        assertNull(ward.wing());
        assertEquals(4, ward.bedsAvailable());
        assertEquals(List.of("row 8: wing missing"), ward.notes());
    }

    @Test
    void shortRowDoesNotCrash() {
        Ward ward = WardCleaner.cleanRow(new String[] {"W-99", "East Wing"}, 3);

        assertNotNull(ward);
        assertNull(ward.department());
        assertNull(ward.bedsAvailable());
        assertEquals(2, ward.notes().size());
    }

    @Test
    void rowWithoutAnIdIsRejectedAndReported() {
        List<String[]> rows = List.of(
                new String[] {"W-01", "East Wing", "Cardiology", "3"},
                new String[] {"N/A", "East Wing", "Cardiology", "3"});

        WardCleaner.Result result = WardCleaner.clean(rows);

        assertEquals(1, result.wards().size());
        assertEquals(List.of("row 2: no usable ward id - dropped"), result.rejected());
    }

    @Test
    void cleansEveryRowOfTheRealFile() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/wards-outdated.csv")) {
            WardCleaner.Result result = WardCleaner.clean(WardCsvReader.readRows(in));

            // 18 rows in, 18 wards out: duplicates are NOT merged yet (next piece)
            assertEquals(18, result.wards().size());
            assertTrue(result.rejected().isEmpty());
        }
    }
}