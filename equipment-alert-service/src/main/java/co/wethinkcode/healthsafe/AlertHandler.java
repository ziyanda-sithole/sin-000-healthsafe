package co.wethinkcode.healthsafe;

// What to do with a delivered alert. Throwing means "not handled": the broker will redeliver it.
@FunctionalInterface
public interface AlertHandler {
    void handle(EquipmentFailureAlert alert) throws Exception;
}