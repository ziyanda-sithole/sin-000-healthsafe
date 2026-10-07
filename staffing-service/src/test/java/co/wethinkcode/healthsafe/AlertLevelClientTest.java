package co.wethinkcode.healthsafe;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertLevelClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startFakeAlertService(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/alert-level", exchange -> {
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
    void parsesAValidLevel() throws IOException {
        assertEquals(0, AlertLevelClient.parseLevel("{\"level\":0}"));
        assertEquals(8, AlertLevelClient.parseLevel("{\"level\":8}"));
    }

    @Test
    void rejectsRepliesWithoutAnIntegerLevel() {
        for (String bad : new String[] {"{}", "{\"level\":\"3\"}", "{\"level\":3.5}",
                "{\"level\":null}", "[]", "not json"}) {
            assertThrows(IOException.class, () -> AlertLevelClient.parseLevel(bad), "for: " + bad);
        }
    }

    @Test
    void rejectsImpossibleLevels() {
        assertThrows(IOException.class, () -> AlertLevelClient.parseLevel("{\"level\":9}"));
        assertThrows(IOException.class, () -> AlertLevelClient.parseLevel("{\"level\":-1}"));
    }

    @Test
    void currentLevelReadsFromTheService() throws Exception {
        String baseUrl = startFakeAlertService(200, "{\"level\":5}");

        assertEquals(5, new AlertLevelClient(baseUrl).currentLevel());
    }

    @Test
    void currentLevelFailsClearlyOnNon200() throws Exception {
        String baseUrl = startFakeAlertService(503, "down");

        IOException error = assertThrows(IOException.class,
                () -> new AlertLevelClient(baseUrl).currentLevel());

        assertEquals("alert-level-service returned HTTP 503", error.getMessage());
    }

    @Test
    void unreachableServiceIsAnErrorThatNamesTheService() {
        IOException error = assertThrows(IOException.class,
                () -> new AlertLevelClient("http://localhost:1").currentLevel());

        assertTrue(error.getMessage().contains("alert-level-service"), error.getMessage());
    }
}