package co.wethinkcode.healthsafe;

import java.util.List;

/**
 * A ward as ward-service sees it. Mirrors the JSON produced by
 * ingestion-service's GET /wards.
 *
 * This is deliberately a COPY of ingestion-service's Ward: each service is an
 * independent Maven project with no shared module, so the two sides agree on a
 * JSON shape (the contract), not on a shared Java class.
 */
public record Ward(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        List<String> notes) {
}