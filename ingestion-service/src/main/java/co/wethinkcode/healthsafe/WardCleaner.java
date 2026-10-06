package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        // LinkedHashMap keeps first-seen order, so output order is stable.
        Map<String, Ward> byId = new LinkedHashMap<>();
        Map<String, Integer> firstRowOf = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();

        int rowNo = 0;
        for (String[] cols : rows) {
            rowNo++;
            Ward ward = cleanRow(cols, rowNo);
            if (ward == null) {
                rejected.add("row " + rowNo + ": no usable ward id - dropped");
                continue;
            }
            Ward existing = byId.get(ward.wardId());
            if (existing == null) {
                byId.put(ward.wardId(), ward);
                firstRowOf.put(ward.wardId(), rowNo);
            } else {
                byId.put(ward.wardId(),
                        merge(existing, ward, firstRowOf.get(ward.wardId()), rowNo));
            }
        }
        return new Result(new ArrayList<>(byId.values()), rejected);
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

    /**
     * Merge two rows describing the same ward.
     * Rule: keep the earlier row's value; only use the later row to FILL GAPS.
     * If both have a value but they disagree, keep the earlier and record the conflict
     * so a human can decide - we never silently overwrite.
     */
    static Ward merge(Ward first, Ward later, int firstRow, int laterRow) {
        List<String> notes = new ArrayList<>(first.notes());
        notes.addAll(later.notes());
        notes.add("merged duplicate of rows " + firstRow + " and " + laterRow);

        String wing = pick("wing", first.wing(), later.wing(), notes);
        String department = pick("department", first.department(), later.department(), notes);
        Integer beds = pick("bedsAvailable", first.bedsAvailable(), later.bedsAvailable(), notes);
        return new Ward(first.wardId(), wing, department, beds, notes);
    }

    private static <T> T pick(String field, T first, T later, List<String> notes) {
        if (first == null) {
            return later;
        }
        if (later != null && !first.equals(later)) {
            notes.add("conflict on " + field + ": kept '" + first + "', ignored '" + later + "'");
        }
        return first;
    }

    // Safe column access: short rows give "" instead of ArrayIndexOutOfBounds.
    private static String col(String[] cols, int i) {
        return i < cols.length ? cols[i] : "";
    }
}