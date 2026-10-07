package co.wethinkcode.healthsafe;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Asks ward-service whether a ward exists.
 * Contract: GET {baseUrl}/wards/{id} -> 200 if known, 404 if unknown.
 */
public final class WardClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final String baseUrl;

    public WardClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * true  = ward-service knows this ward (200)
     * false = ward-service says it does not exist (404)
     * throws = we could not get a trustworthy answer (service down, 500, timeout...)
     */
    public boolean wardExists(String wardId) throws IOException, InterruptedException {
        // URLEncoder is meant for form data and turns spaces into '+', which is wrong in a path.
        String safeId = URLEncoder.encode(wardId, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/wards/" + safeId)).timeout(Duration.ofSeconds(5)).GET().build();

        HttpResponse<Void> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // Java's HttpClient often throws ConnectException with a null message,
            // so say which service we could not reach, and keep the original as the cause.
            throw new IOException("could not reach ward-service at " + baseUrl
                    + " (" + e.getClass().getSimpleName() + ")", e);
        }

        return switch (response.statusCode()) {
            case 200 -> true;
            case 404 -> false;
            default -> throw new IOException("ward-service returned HTTP " + response.statusCode());
        };
    }
}