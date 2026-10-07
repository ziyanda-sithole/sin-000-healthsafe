package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public class StaffingServiceApp {

    private static final int PORT = 7033;
    private static final String WARD_URL =
            System.getenv().getOrDefault("WARD_URL", "http://localhost:7031");
    private static final String ALERT_LEVEL_URL =
            System.getenv().getOrDefault("ALERT_LEVEL_URL", "http://localhost:7032");

    public static void main(String[] args) {
        WardClient wards = new WardClient(WARD_URL);
        AlertLevelClient alertLevels = new AlertLevelClient(ALERT_LEVEL_URL);

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));

        // On-call schedule for a ward: validate the ward, read the level, apply the policy.
        app.get("/schedule/{id}", ctx -> {
            String wardId = ctx.pathParam("id").trim().toUpperCase(Locale.ROOT);
            try {
                if (!wards.wardExists(wardId)) {
                    ctx.status(404).json(Map.of("error", "Unknown ward: " + wardId));
                    return;
                }
                int level = alertLevels.currentLevel();
                ctx.json(StaffingPolicy.scheduleFor(wardId, level));
            } catch (IOException e) {
                // A dependency is down or misbehaving: not the caller's fault, not ours either.
                ctx.status(502).json(Map.of("error", "Cannot build schedule: " + e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ctx.status(503).json(Map.of("error", "Request interrupted"));
            }
        });
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)