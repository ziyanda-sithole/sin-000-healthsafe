package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory store of the wards fetched from ingestion-service.
 * Read-only after construction, so it is safe to share between request threads.
 */
public final class WardRepository {

    private final Map<String, Ward> byId = new LinkedHashMap<>();

    public WardRepository(List<Ward> wards) {
        for (Ward ward : wards) {
            byId.put(key(ward.wardId()), ward);
        }
    }

    public List<Ward> findAll() {
        return new ArrayList<>(byId.values());
    }

    // Lookup ignores case and padding: " w-05 " finds W-05.
    public Optional<Ward> findById(String id) {
        return Optional.ofNullable(byId.get(key(id)));
    }

    // Distinct department names, alphabetical, skipping wards with no department.
    public List<String> departments() {
        return byId.values().stream().map(Ward::department).filter(d -> d != null).distinct().sorted().toList();
    }

    private static String key(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }
}