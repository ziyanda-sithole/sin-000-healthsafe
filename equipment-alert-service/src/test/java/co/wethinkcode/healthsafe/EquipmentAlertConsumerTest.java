package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.Test;

class EquipmentAlertConsumerTest {

    private static final String QUEUE = "test-equipment-failure-queue";
    private static final String ALERT = """
            {"alertId":"a-1","wardId":"W-05","equipment":"ventilator",
             "details":"no airflow","reportedAtMillis":1791360000000}""";

    private static String freeBrokerUrl() throws IOException {
        try (ServerSocket free = new ServerSocket(0)) {
            return "tcp://localhost:" + free.getLocalPort();
        }
    }

    private static BrokerService startBroker(String url) throws Exception {
        BrokerService broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector(url);
        broker.start();
        return broker;
    }

    private static void publish(String url, String text) throws JMSException {
        Connection connection = new ActiveMQConnectionFactory(url).createConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(QUEUE));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(session.createTextMessage(text));
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
    void deliversAnAlertSentWhileNoConsumerWasRunning() throws Exception {
        String url = freeBrokerUrl();
        BrokerService broker = startBroker(url);
        try {
            publish(url, ALERT); // nobody is consuming yet: the alert must wait on the queue

            List<String> received = new CopyOnWriteArrayList<>();
            EquipmentAlertConsumer consumer =
                    new EquipmentAlertConsumer(url, QUEUE, alert -> received.add(alert.alertId()), 100);
            consumer.start();
            try {
                assertTrue(await(() -> received.contains("a-1"), 10000),
                        "the waiting alert was never delivered");
            } finally {
                consumer.stop();
            }
        } finally {
            broker.stop();
        }
    }

    @Test
    void redeliversAnAlertWhoseHandlerFailed() throws Exception {
        String url = freeBrokerUrl();
        BrokerService broker = startBroker(url);
        try {
            AtomicInteger attempts = new AtomicInteger();
            List<String> handled = new CopyOnWriteArrayList<>();
            EquipmentAlertConsumer consumer = new EquipmentAlertConsumer(url, QUEUE, alert -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("first attempt fails");
                }
                handled.add(alert.alertId());
            }, 100);
            consumer.start();
            try {
                assertTrue(await(consumer::isConsuming, 5000), "never started consuming");
                publish(url, ALERT);

                // the default redelivery delay is about a second, hence the generous timeout
                assertTrue(await(() -> handled.contains("a-1"), 15000), "the alert was never redelivered");
                assertEquals(2, attempts.get());
            } finally {
                consumer.stop();
            }
        } finally {
            broker.stop();
        }
    }

    @Test
    void anAlertThatCannotBeReadEndsUpInTheDeadLetterQueue() throws Exception {
        String url = freeBrokerUrl();
        BrokerService broker = startBroker(url);
        EquipmentAlertConsumer consumer = new EquipmentAlertConsumer(url, QUEUE, alert -> { }, 100);
        Connection connection = new ActiveMQConnectionFactory(url).createConnection();
        try {
            consumer.start();
            assertTrue(await(consumer::isConsuming, 5000), "never started consuming");
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer deadLetters = session.createConsumer(session.createQueue("ActiveMQ.DLQ"));

            publish(url, "not json");

            // after several failed redeliveries the broker parks it here instead of dropping it
            Message parked = deadLetters.receive(30000);
            assertNotNull(parked, "the unreadable alert was lost instead of dead-lettered");
            assertEquals("not json", ((TextMessage) parked).getText());
        } finally {
            consumer.stop();
            connection.close();
            broker.stop();
        }
    }
}