package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WardCsvReaderTest {

    private static InputStream streamOf(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void skipsHeaderAndReturnsRawRowsUntouched() throws IOException {
        List<String[]> rows = WardCsvReader.readRows(
                streamOf("ward_id,wing,department,beds\nw-05, east wing ,PAEDIATRICS,five\n"));

        assertEquals(1, rows.size());
        // still dirty on purpose: cleaning is not this class's job
        assertArrayEquals(new String[] {"w-05", " east wing ", "PAEDIATRICS", "five"}, rows.get(0));
    }

    @Test
    void keepsTrailingEmptyFields() throws IOException {
        List<String[]> rows = WardCsvReader.readRows(streamOf("h1,h2,h3,h4\nW-08,,Oncology,\n"));

        assertEquals(4, rows.get(0).length);
        assertEquals("", rows.get(0)[1]);
        assertEquals("", rows.get(0)[3]);
    }

    @Test
    void skipsBlankLinesAndHandlesEmptyInput() throws IOException {
        assertEquals(1, WardCsvReader.readRows(streamOf("h\nW-01,a,b,1\n\n   \n")).size());
        assertTrue(WardCsvReader.readRows(streamOf("")).isEmpty());
    }

    @Test
    void readsTheRealLegacyFile() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/wards-outdated.csv")) {
            assertNotNull(in, "wards-outdated.csv should be on the classpath");
            assertEquals(18, WardCsvReader.readRows(in).size());
        }
    }
}