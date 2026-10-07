package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Calls ingestion-service over HTTP to get the cleaned ward records.
 * Contract: GET {baseUrl}/wards -> JSON array of wards.
 */
public final class IngestionClient {

    // Be liberal in what we accept: if ingestion adds a new field later,
    // ignore it instead of crashing this service.
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;

    public IngestionClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // Fetches and parses the ward list. Throws if ingestion is down or replies badly.
    public List<Ward> fetchWards() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/wards")).timeout(Duration.ofSeconds(5)).GET().build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("could not reach ingestion-service at " + baseUrl
                    + " (" + e.getClass().getSimpleName() + ")", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("ingestion-service returned HTTP " + response.statusCode());
        }
        return parseWards(response.body());
    }

    // Separate from the HTTP call so JSON parsing can be tested without a server.
    static List<Ward> parseWards(String json) throws IOException {
        return MAPPER.readValue(json, new TypeReference<List<Ward>>() { });
    }
}