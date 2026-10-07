package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IngestionClientTest {

    private static final String ONE_WARD = """
            [{"wardId":"W-08","wing":null,"department":"Oncology",
              "bedsAvailable":4,"notes":["row 9: wing missing"]}]""";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Starts a tiny fake ingestion-service on a free port and returns its base URL. */
    private String startFakeIngestion(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/wards", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void parsesWardsIncludingNullFields() throws IOException {
        List<Ward> wards = IngestionClient.parseWards(ONE_WARD);

        assertEquals(1, wards.size());
        assertEquals("W-08", wards.get(0).wardId());
        assertNull(wards.get(0).wing());
        assertEquals(4, wards.get(0).bedsAvailable());
        assertEquals(List.of("row 9: wing missing"), wards.get(0).notes());
    }

    @Test
    void ignoresFieldsItDoesNotKnow() throws IOException {
        String json = "[{\"wardId\":\"W-01\",\"wing\":\"East Wing\",\"department\":\"ICU\","
                + "\"bedsAvailable\":2,\"notes\":[],\"newFieldFromTheFuture\":true}]";

        assertEquals("W-01", IngestionClient.parseWards(json).get(0).wardId());
    }

    @Test
    void fetchWardsReadsFromTheService() throws Exception {
        String baseUrl = startFakeIngestion(200, ONE_WARD);

        List<Ward> wards = new IngestionClient(baseUrl).fetchWards();

        assertEquals(1, wards.size());
        assertEquals("Oncology", wards.get(0).department());
    }

    @Test
    void fetchWardsFailsClearlyOnNon200() throws Exception {
        String baseUrl = startFakeIngestion(500, "boom");

        IOException error = assertThrows(IOException.class,
                () -> new IngestionClient(baseUrl).fetchWards());

        assertEquals("ingestion-service returned HTTP 500", error.getMessage());
    }
}