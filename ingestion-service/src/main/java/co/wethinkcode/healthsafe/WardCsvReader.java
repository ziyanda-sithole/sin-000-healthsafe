package co.wethinkcode.healthsafe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the raw CSV file into rows of raw (still dirty) strings.
 * Deliberately dumb: it does NOT clean anything. Cleaning lives in WardCleaner,
 * so this class is the only place that cares about "how is the file stored".
 * Limitation: splits on every comma, so quoted fields containing commas
 * ("Ward A, Annex") are not supported. wards-outdated.csv has none.
 */
public final class WardCsvReader {

    private WardCsvReader() {
    }

    // Reads every data row (the header line is skipped).
    public static List<String[]> readRows(InputStream in) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // column names - we rely on column ORDER instead
            if (header == null) {
                return rows;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                // limit -1 keeps trailing empty fields: "W-08,,Oncology," -> 4 parts, not 3
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }
}