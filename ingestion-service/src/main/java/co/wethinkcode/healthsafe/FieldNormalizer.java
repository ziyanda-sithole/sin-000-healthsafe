package co.wethinkcode.healthsafe;

import java.util.Locale;
import java.util.Set;

/**
 * Small, stateless helpers that each fix ONE kind of mess.
 * Keeping them separate from the CSV reading means each rule can be
 * understood (and tested) on its own.
 */
public final class FieldNormalizer {

    // Values that mean "no real value here" in the legacy export.
    private static final Set<String> PLACEHOLDERS =
            Set.of("", "n/a", "na", "tbd", "unknown", "-", "nan", "null");

    private FieldNormalizer() {
    }

    // Trim ends and collapse inner runs of whitespace: "South  Wing " -> "South Wing".
    public static String tidy(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    // True for blank/N/A/TBD/unknown/-/NaN (any casing, any padding).
    public static boolean isPlaceholder(String raw) {
        return PLACEHOLDERS.contains(tidy(raw).toLowerCase(Locale.ROOT));
    }

    // " w-05 " -> "W-05". Returns null if the id is missing.
    public static String normalizeId(String raw) {
        String t = tidy(raw);
        return isPlaceholder(t) ? null : t.toUpperCase(Locale.ROOT);
    }

    // "east  wing " -> "East Wing". Returns null if missing.
    public static String normalizeWing(String raw) {
        String t = tidy(raw);
        return isPlaceholder(t) ? null : titleCase(t);
    }

    // "east wing" -> "East Wing".
    static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}