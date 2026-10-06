package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleans raw CSV rows into Ward records.

 * Column order expected: ward_id, wing, department, beds_available.
 * "Row N" in notes means the N-th DATA row (header not counted), matching the
 * numbering used in ingestion-service/README.md.
 */
public final class WardCleaner {

    // Outcome of a cleaning run: good records plus any rows we had to drop.
    public record Result(List<Ward> wards, List<String> rejected) {
    }

    private WardCleaner() {
    }

    public static Result clean(List<String[]> rows) {
        List<Ward> wards = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        int rowNo = 0;
        for (String[] cols : rows) {
            rowNo++;
            Ward ward = cleanRow(cols, rowNo);
            if (ward == null) {
                rejected.add("row " + rowNo + ": no usable ward id - dropped");
            } else {
                wards.add(ward);
            }
        }
        return new Result(wards, rejected);
    }

    // Cleans one row. Returns null when there is no ward id (we cannot identify it).
    static Ward cleanRow(String[] cols, int rowNo) {
        String id = FieldNormalizer.normalizeId(col(cols, 0));
        if (id == null) {
            return null;
        }
        String wing = FieldNormalizer.normalizeWing(col(cols, 1));
        String department = FieldNormalizer.normalizeDepartment(col(cols, 2));
        FieldNormalizer.BedsResult beds = FieldNormalizer.parseBeds(col(cols, 3));

        List<String> notes = new ArrayList<>();
        if (wing == null) {
            notes.add("row " + rowNo + ": wing missing");
        }
        if (department == null) {
            notes.add("row " + rowNo + ": department missing");
        }
        if (beds.note() != null) {
            notes.add("row " + rowNo + ": " + beds.note());
        }
        return new Ward(id, wing, department, beds.value(), notes);
    }

    // Safe column access: short rows give "" instead of ArrayIndexOutOfBounds.
    private static String col(String[] cols, int i) {
        return i < cols.length ? cols[i] : "";
    }
}