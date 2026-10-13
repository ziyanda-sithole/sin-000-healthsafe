package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;

import java.io.IOException;
import javax.jms.JMSException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WardServiceApp {

    private static final int PORT = 7031;
    private static final String INGESTION_URL =
            System.getenv().getOrDefault("INGESTION_URL", "http://localhost:7030");
    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 2000;

    // Body of POST /wards/{id}/equipment-failure, e.g. {"equipment":"ventilator","details":"no airflow"}.
    record EquipmentFailureReport(String equipment, String details) {
    }

    public static void main(String[] args) throws InterruptedException {
        WardRepository repository = new WardRepository(loadWards());
        System.out.println("Loaded " + repository.findAll().size() + " wards from " + INGESTION_URL);
        // Listens for staffing updates in the background. If the broker is down the service
        // still starts and serves wards; the subscription connects as soon as the broker is up.
        StaffingStore staffing = new StaffingStore();
        new StaffingEventSubscriber(MqConfig.BROKER_URL, MqConfig.TOPIC, staffing, 5000).start();

        // Does not connect yet: a connection is opened per alert, so a broker that is down
        // never stops this service from starting or from serving wards.
        EquipmentAlertPublisher alertPublisher =
                new EquipmentAlertPublisher(MqConfig.BROKER_URL, MqConfig.QUEUE);

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

        // Latest staffing update received over the topic for this ward.
        app.get("/wards/{id}/staffing", ctx -> {
            String id = ctx.pathParam("id");
            if (repository.findById(id).isEmpty()) {
                ctx.status(404).json(Map.of("error", "Unknown ward: " + id));
                return;
            }
            staffing.find(id).ifPresentOrElse(
                    event -> ctx.json(WardStaffing.from(event)),
                    () -> ctx.status(404).json(Map.of("error", "No staffing update received yet for ward: " + id)));
        });

        // Report a critical equipment failure: queued for equipment-alert-service, which is
        // guaranteed to get it (see equipment-alert-service/README.md).
        app.post("/wards/{id}/equipment-failure", ctx -> {
            String id = ctx.pathParam("id");
            Optional<Ward> ward = repository.findById(id);
            if (ward.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Unknown ward: " + id));
                return;
            }
            EquipmentFailureReport report;
            try {
                report = ctx.bodyAsClass(EquipmentFailureReport.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error",
                        "body must be JSON like {\"equipment\": \"ventilator\", \"details\": \"no airflow\"}"));
                return;
            }
            if (report.equipment() == null || report.equipment().isBlank()) {
                ctx.status(400).json(Map.of("error", "body must contain a non-empty 'equipment'"));
                return;
            }
            EquipmentFailureAlert alert = new EquipmentFailureAlert(
                    UUID.randomUUID().toString(),
                    ward.get().wardId(),
                    report.equipment().trim(),
                    report.details() == null || report.details().isBlank() ? null : report.details().trim(),
                    System.currentTimeMillis());
            try {
                alertPublisher.publish(alert);
            } catch (JMSException e) {
                System.err.println("Could not queue " + alert + ": " + e);
                ctx.status(502).json(Map.of("error", "Alert NOT sent: could not use the message broker at "
                        + MqConfig.BROKER_URL + ". Report the failure another way."));
                return;
            }
            ctx.status(202).json(alert); // accepted for processing, not yet processed
        });

        app.get("/departments", ctx -> ctx.json(repository.departments()));
    }

    // ingestion-service may still be starting, so retry a few times before giving up.
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
