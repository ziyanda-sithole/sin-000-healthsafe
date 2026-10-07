package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class WardServiceApp {

    private static final int PORT = 7031;
    private static final String INGESTION_URL =
            System.getenv().getOrDefault("INGESTION_URL", "http://localhost:7030");
    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 2000;

    public static void main(String[] args) throws InterruptedException {
        WardRepository repository = new WardRepository(loadWards());
        System.out.println("Loaded " + repository.findAll().size() + " wards from " + INGESTION_URL);

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> ctx.json(repository.findAll()));

        // Contract: staffing-service calls this to validate a ward; 404 if unknown.
        app.get("/wards/{id}", ctx -> {
            String id = ctx.pathParam("id");
            repository.findById(id).ifPresentOrElse(
                    ward -> ctx.json(ward),
                    () -> ctx.status(404).json(Map.of("error", "Unknown ward: " + id)));
        });

        app.get("/departments", ctx -> ctx.json(repository.departments()));
    }

    /** ingestion-service may still be starting, so retry a few times before giving up. */
    private static List<Ward> loadWards() throws InterruptedException {
        IngestionClient client = new IngestionClient(INGESTION_URL);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return client.fetchWards();
            } catch (IOException e) {
                System.err.println("Attempt " + attempt + "/" + MAX_ATTEMPTS
                        + " to reach ingestion-service failed: " + e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }
        }
        throw new IllegalStateException(
                "Could not load wards from " + INGESTION_URL + " - is ingestion-service running?");
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.