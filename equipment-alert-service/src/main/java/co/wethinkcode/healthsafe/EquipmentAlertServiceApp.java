package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;

public class EquipmentAlertServiceApp {

    private static final int PORT = 7034;
    private static final long RETRY_DELAY_MS = 5000;

    public static void main(String[] args) {
        AlertLog log = new AlertLog();

        // Connects in the background and retries: the service starts even if the broker is down.
        new EquipmentAlertConsumer(MqConfig.BROKER_URL, MqConfig.QUEUE,
                alert -> handle(alert, log), RETRY_DELAY_MS).start();

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));

        // Every distinct alert processed so far, newest first.
        app.get("/alerts", ctx -> ctx.json(log.newestFirst()));
    }

    // Raises the alarm. Called at least once per alert, so duplicates are recognised by id.
    static void handle(EquipmentFailureAlert alert, AlertLog log) {
        if (log.record(alert)) {
            System.out.println("*** EQUIPMENT FAILURE on ward " + alert.wardId() + ": " + alert.equipment()
                    + (alert.details() == null ? "" : " (" + alert.details() + ")")
                    + " [alert " + alert.alertId() + "]");
        } else {
            System.out.println("Duplicate of alert " + alert.alertId() + " ignored");
        }
    }
}