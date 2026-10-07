package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class IngestionServiceApp {

    private static final int PORT = 7030;
    private static final String CSV_RESOURCE = "/wards-outdated.csv";

    public static void main(String[] args) throws IOException {
        // The CSV never changes while we run, so clean it ONCE at startup
        // instead of re-parsing on every request.
        WardCleaner.Result result = loadAndClean();
        List<Ward> wards = result.wards();
        System.out.println("Loaded " + wards.size() + " wards; rejected rows: " + result.rejected());

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));

        // Contract: ward-service calls GET /wards to populate its own list.
        // ctx.json(...) serialises the records to a JSON array via Jackson.
        app.get("/wards", ctx -> ctx.json(wards));
    }

    private static WardCleaner.Result loadAndClean() throws IOException {
        try (InputStream in = IngestionServiceApp.class.getResourceAsStream(CSV_RESOURCE)) {
            if (in == null) {
                // Fail fast with a clear message rather than a NullPointerException later.
                throw new IllegalStateException("Missing classpath resource " + CSV_RESOURCE);
            }
            return WardCleaner.clean(WardCsvReader.readRows(in));
        }
    }
}