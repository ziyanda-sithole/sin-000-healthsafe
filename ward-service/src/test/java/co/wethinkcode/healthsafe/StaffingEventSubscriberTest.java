package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.util.function.BooleanSupplier;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.Test;

class StaffingEventSubscriberTest {

    private static final String TOPIC = "test-staffing-events";
    private static final String VALID = """
            {"type":"ScheduleUpdated","wardId":"W-05","alertLevel":8,
             "status":"CODE_BLUE","doctorsOnCall":9,"issuedAtMillis":1791360000000}""";

    // A private broker inside this JVM; each test uses its own name so they never share one.
    private static String inMemoryBroker(String name) {
        return "vm://" + name + "?broker.persistent=false&broker.useJmx=false";
    }

    private static void publish(String brokerUrl, String text) throws JMSException {
        Connection connection = new ActiveMQConnectionFactory(brokerUrl).createConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            session.createProducer(session.createTopic(TOPIC)).send(session.createTextMessage(text));
        } finally {
            connection.close();
        }
    }

    private static boolean await(BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25);
        }
        return condition.getAsBoolean();
    }

    @Test
    void storesAnEventPublishedOnTheTopic() throws Exception {
        String broker = inMemoryBroker("ward-sub-1");
        StaffingStore store = new StaffingStore();
        StaffingEventSubscriber subscriber = new StaffingEventSubscriber(broker, TOPIC, store, 100);
        subscriber.start();
        try {
            // subscribe BEFORE publishing: a topic does not keep messages for late subscribers
            assertTrue(await(subscriber::isSubscribed, 5000), "never subscribed");

            publish(broker, VALID);

            assertTrue(await(() -> store.find("W-05").isPresent(), 5000), "event never arrived");
            assertEquals(9, store.find("W-05").orElseThrow().doctorsOnCall());
        } finally {
            subscriber.stop();
        }
    }

    @Test
    void keepsListeningAfterUnreadableMessages() throws Exception {
        String broker = inMemoryBroker("ward-sub-2");
        StaffingStore store = new StaffingStore();
        StaffingEventSubscriber subscriber = new StaffingEventSubscriber(broker, TOPIC, store, 100);
        subscriber.start();
        try {
            assertTrue(await(subscriber::isSubscribed, 5000), "never subscribed");

            publish(broker, "not json");
            publish(broker, VALID.replace("ScheduleUpdated", "SomethingElse"));
            publish(broker, VALID);

            assertTrue(await(() -> store.find("W-05").isPresent(), 5000),
                    "the valid event after the garbage never arrived");
        } finally {
            subscriber.stop();
        }
    }

    @Test
    void startsWithoutABrokerAndSubscribesWhenOneAppears() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort(); // ask the OS for an unused port
        }
        String broker = "tcp://localhost:" + port;
        StaffingStore store = new StaffingStore();
        StaffingEventSubscriber subscriber = new StaffingEventSubscriber(broker, TOPIC, store, 100);

        subscriber.start(); // must not throw even though nothing is listening
        assertFalse(subscriber.isSubscribed());

        BrokerService brokerService = new BrokerService();
        brokerService.setPersistent(false);
        brokerService.setUseJmx(false);
        brokerService.addConnector(broker);
        brokerService.start();
        try {
            assertTrue(await(subscriber::isSubscribed, 10000), "never subscribed after broker appeared");

            publish(broker, VALID);

            assertTrue(await(() -> store.find("W-05").isPresent(), 5000), "event never arrived");
        } finally {
            subscriber.stop();
            brokerService.stop();
        }
    }

    @Test
    void stopEndsTheSubscription() throws Exception {
        String broker = inMemoryBroker("ward-sub-4");
        StaffingEventSubscriber subscriber =
                new StaffingEventSubscriber(broker, TOPIC, new StaffingStore(), 100);
        subscriber.start();
        assertTrue(await(subscriber::isSubscribed, 5000), "never subscribed");

        subscriber.stop();

        assertTrue(await(() -> !subscriber.isSubscribed(), 5000), "still subscribed after stop()");
    }
}