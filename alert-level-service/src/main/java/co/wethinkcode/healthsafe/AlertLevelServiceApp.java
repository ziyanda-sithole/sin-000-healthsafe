package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;

public class AlertLevelServiceApp {

    private static final int PORT = 7032;

    // Body of PUT /alert-level, e.g. {"level": 4}. Integer (not int) so a missing field is null.
    record LevelRequest(Integer level) {
    }

    public static void main(String[] args) {
        AlertLevelStore store = new AlertLevelStore();

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));

        // Contract: staffing-service calls this to size the on-call schedule.
        app.get("/alert-level", ctx -> ctx.json(Map.of("level", store.get())));

        // Raise or lower the Emergency Status.
        // Raise or lower the Emergency Status.
        app.put("/alert-level", ctx -> {
            LevelRequest request;
            try {
                request = ctx.bodyAsClass(LevelRequest.class);
            } catch (Exception e) {
                // Unparseable JSON, or 'level' is not a number: the caller's mistake, so 400.
                ctx.status(400).json(Map.of("error", "body must be JSON like {\"level\": 4}"));
                return;
            }
            if (request.level() == null) {
                ctx.status(400).json(Map.of("error", "body must contain a numeric 'level'"));
                return;
            }
            try {
                store.set(request.level());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
                return;
            }
            ctx.json(Map.of("level", store.get()));
        });
    }
}