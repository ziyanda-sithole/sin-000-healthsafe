package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WardClientTest {

    private HttpServer server;
    private final List<String> rawPathsSeen = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // Fake ward-service: W-05 exists, W-99 does not, anything else is a server error.
    private String startFakeWardService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/wards/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            rawPathsSeen.add(path);
            int status = path.endsWith("W-05") ? 200 : path.endsWith("W-99") ? 404 : 500;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void knownWardExists() throws Exception {
        assertTrue(new WardClient(startFakeWardService()).wardExists("W-05"));
    }

    @Test
    void unknownWardDoesNotExist() throws Exception {
        assertFalse(new WardClient(startFakeWardService()).wardExists("W-99"));
    }

    @Test
    void serverErrorIsAnErrorNotAnAnswer() throws Exception {
        WardClient client = new WardClient(startFakeWardService());

        IOException error = assertThrows(IOException.class, () -> client.wardExists("W-50"));

        assertEquals("ward-service returned HTTP 500", error.getMessage());
    }

    @Test
    void unreachableServiceIsAnErrorNotFalse() {
        // port 1 has nothing listening: we must NOT report "ward does not exist"
        WardClient client = new WardClient("http://localhost:1");

        IOException error = assertThrows(IOException.class, () -> client.wardExists("W-05"));

        assertTrue(error.getMessage().contains("ward-service"), error.getMessage());
        assertTrue(error.getMessage().contains("http://localhost:1"), error.getMessage());
    }

    @Test
    void idsAreUrlEncodedInThePath() throws Exception {
        WardClient client = new WardClient(startFakeWardService());

        assertThrows(IOException.class, () -> client.wardExists("W 05")); // fake answers 500

        assertEquals(List.of("/wards/W%2005"), rawPathsSeen);
    }
}