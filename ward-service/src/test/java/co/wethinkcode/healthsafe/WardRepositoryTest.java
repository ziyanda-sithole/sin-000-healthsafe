package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WardRepositoryTest {

    private final WardRepository repo = new WardRepository(List.of(
            new Ward("W-01", "East Wing", "Cardiology", 3, List.of()),
            new Ward("W-02", "West Wing", "Paediatrics", null, List.of()),
            new Ward("W-03", null, null, 1, List.of()),
            new Ward("W-04", "North Wing", "Cardiology", 2, List.of())));

    @Test
    void findAllKeepsTheOriginalOrder() {
        List<String> ids = repo.findAll().stream().map(Ward::wardId).toList();

        assertEquals(List.of("W-01", "W-02", "W-03", "W-04"), ids);
    }

    @Test
    void findByIdIgnoresCaseAndPadding() {
        assertEquals("W-02", repo.findById(" w-02 ").orElseThrow().wardId());
    }

    @Test
    void findByIdIsEmptyForUnknownOrNullIds() {
        assertTrue(repo.findById("W-99").isEmpty());
        assertTrue(repo.findById(null).isEmpty());
    }

    @Test
    void departmentsAreDistinctSortedAndSkipMissingOnes() {
        assertEquals(List.of("Cardiology", "Paediatrics"), repo.departments());
    }
}