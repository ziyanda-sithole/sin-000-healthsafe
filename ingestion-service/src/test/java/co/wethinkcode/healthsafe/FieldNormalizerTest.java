package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FieldNormalizerTest {

    @Test
    void normalizeIdTrimsAndUppercases() {
        assertEquals("W-05", FieldNormalizer.normalizeId(" w-05 "));
    }

    @Test
    void placeholdersBecomeNull() {
        assertNull(FieldNormalizer.normalizeId("N/A"));
        assertNull(FieldNormalizer.normalizeWing(""));
        assertNull(FieldNormalizer.normalizeDepartment("TBD"));
    }

    @Test
    void wingCollapsesSpacesAndTitleCases() {
        assertEquals("South Wing", FieldNormalizer.normalizeWing("south  Wing "));
    }

    @Test
    void departmentSpellingVariantsAreUnified() {
        assertEquals("Paediatrics", FieldNormalizer.normalizeDepartment("PAEDIATRICS"));
        assertEquals("Paediatrics", FieldNormalizer.normalizeDepartment("Pediatrics"));
    }

    @Test
    void departmentKeepsIcuAcronymAndTitleCasesOthers() {
        assertEquals("ICU", FieldNormalizer.normalizeDepartment("icu"));
        assertEquals("Cardiology", FieldNormalizer.normalizeDepartment("cardiology"));
    }

    @Test
    void validBedCountsAreAcceptedIncludingZero() {
        assertEquals(3, FieldNormalizer.parseBeds("3").value());
        assertEquals(0, FieldNormalizer.parseBeds("0").value());
        assertNull(FieldNormalizer.parseBeds("3").note());
    }

    @Test
    void badBedCountsBecomeNullWithAReason() {
        for (String bad : new String[] {"N/A", "five", "-1", "2023", "TBD", "full"}) {
            FieldNormalizer.BedsResult result = FieldNormalizer.parseBeds(bad);
            assertNull(result.value(), "expected null for: " + bad);
            assertNotNull(result.note(), "expected a note for: " + bad);
        }
    }
}