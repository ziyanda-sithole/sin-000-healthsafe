package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.jms.JMSException;

public class StaffingServiceApp {

    private static final int PORT = 7033;
    private static final String WARD_URL =
            System.getenv().getOrDefault("WARD_URL", "http://localhost:7031");
    private static final String ALERT_LEVEL_URL =
            System.getenv().getOrDefault("ALERT_LEVEL_URL", "http://localhost:7032");

    public static void main(String[] args) {
        WardClient wards = new WardClient(WARD_URL);
        AlertLevelClient alertLevels = new AlertLevelClient(ALERT_LEVEL_URL);
        // Does not connect yet: a connection is opened per publish, so a broker that is
        // down never stops this service from starting or from serving GET /schedule.
        ScheduleEventPublisher publisher = new ScheduleEventPublisher(MqConfig.BROKER_URL, MqConfig.TOPIC);

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));

        // On-call schedule for a ward: validate the ward, read the level, apply the policy.
        app.get("/schedule/{id}", ctx ->
                buildSchedule(ctx, wards, alertLevels).ifPresent(schedule -> ctx.json(schedule)));

        // Same schedule, but also broadcast it on the topic so subscribers (ward-service) hear of it.
        app.post("/schedule/{id}/publish", ctx -> {
            Optional<OnCallSchedule> schedule = buildSchedule(ctx, wards, alertLevels);
            if (schedule.isEmpty()) {
                return; // buildSchedule already wrote the error response
            }
            ScheduleUpdatedEvent event =
                    ScheduleUpdatedEvent.from(schedule.get(), System.currentTimeMillis());
            try {
                publisher.publish(event);
            } catch (JMSException e) {
                System.err.println("Could not publish " + event + ": " + e);
                ctx.status(502).json(Map.of("error",
                        "Schedule built but not published: could not use the message broker at "
                                + MqConfig.BROKER_URL));
                return;
            }
            ctx.json(event); // show the caller exactly what was broadcast
        });
    }

    /**
     * Builds the schedule for the ward named in the URL.
     * On success returns it. On any failure it writes the error response itself
     * (404 unknown ward, 502 dependency problem, 503 interrupted) and returns empty,
     * so the caller must simply stop.
     */
    private static Optional<OnCallSchedule> buildSchedule(
            Context ctx, WardClient wards, AlertLevelClient alertLevels) {
        String wardId = ctx.pathParam("id").trim().toUpperCase(Locale.ROOT);
        try {
            if (!wards.wardExists(wardId)) {
                ctx.status(404).json(Map.of("error", "Unknown ward: " + wardId));
                return Optional.empty();
            }
            int level = alertLevels.currentLevel();
            return Optional.of(StaffingPolicy.scheduleFor(wardId, level));
        } catch (IOException e) {
            // A dependency is down or misbehaving: not the caller's fault, not ours either.
            ctx.status(502).json(Map.of("error", "Cannot build schedule: " + e.getMessage()));
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ctx.status(503).json(Map.of("error", "Request interrupted"));
            return Optional.empty();
        }
    }
}
