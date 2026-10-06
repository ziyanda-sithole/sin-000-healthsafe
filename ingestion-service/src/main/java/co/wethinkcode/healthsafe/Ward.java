package co.wethinkcode.healthsafe;

import java.util.List;

public record Ward(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        List<String> notes) {
}