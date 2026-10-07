package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reads the current Emergency Status from alert-level-service.
 * Contract: GET {baseUrl}/alert-level -> {"level": 0-8}
 */
public final class AlertLevelClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final String baseUrl;

    public AlertLevelClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // Returns the current level (0-8), or throws if we cannot get a trustworthy one.
    public int currentLevel() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/alert-level")).timeout(Duration.ofSeconds(5)).GET().build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("alert-level-service returned HTTP " + response.statusCode());
        }
        return parseLevel(response.body());
    }

    // Separate from the HTTP call so parsing can be tested without a server.
    static int parseLevel(String json) throws IOException {
        JsonNode level = MAPPER.readTree(json).get("level");
        if (level == null || !level.isInt()) {
            throw new IOException("alert-level-service reply has no integer 'level': " + json);
        }
        int value = level.intValue();
        if (value < 0 || value > 8) {
            throw new IOException("alert-level-service reported an impossible level: " + value);
        }
        return value;
    }
}