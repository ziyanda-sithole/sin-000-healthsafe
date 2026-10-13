package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.Test;

class EquipmentAlertPublisherTest {

    private static final String QUEUE = "test-equipment-failure-queue";

    private record Received(String text, int deliveryMode) {
    }

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

    // Takes one message off the queue, or returns null if none arrives within 5 seconds.
    private static Received receiveOne(String url) throws JMSException {
        Connection connection = new ActiveMQConnectionFactory(url).createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE));
            Message message = consumer.receive(5000);
            return message == null
                    ? null
                    : new Received(((TextMessage) message).getText(), message.getJMSDeliveryMode());
        } finally {
            connection.close();
        }
    }

    @Test
    void queuesAPersistentJsonAlertThatWaitsForALateConsumer() throws Exception {
        String url = freeBrokerUrl();
        BrokerService broker = startBroker(url);
        try {
            // published BEFORE any consumer exists: a queue holds it, unlike a topic
            new EquipmentAlertPublisher(url, QUEUE).publish(
                    new EquipmentFailureAlert("a-1", "W-05", "ventilator", "no airflow", 1791360000000L));

            Received received = receiveOne(url);

            assertNotNull(received, "the queued alert was never delivered");
            assertEquals(DeliveryMode.PERSISTENT, received.deliveryMode());
            JsonNode json = new ObjectMapper().readTree(received.text());
            assertEquals("a-1", json.get("alertId").asText());
            assertEquals("W-05", json.get("wardId").asText());
            assertEquals("ventilator", json.get("equipment").asText());
            assertEquals("no airflow", json.get("details").asText());
            assertEquals(1791360000000L, json.get("reportedAtMillis").asLong());
            assertEquals(5, json.size()); // exactly the documented fields
        } finally {
            broker.stop();
        }
    }

    @Test
    void anAlertWithoutDetailsStillSerialises() throws Exception {
        String url = freeBrokerUrl();
        BrokerService broker = startBroker(url);
        try {
            new EquipmentAlertPublisher(url, QUEUE).publish(
                    new EquipmentFailureAlert("a-2", "W-05", "monitor", null, 1L));

            JsonNode json = new ObjectMapper().readTree(receiveOne(url).text());

            assertTrue(json.get("details").isNull());
        } finally {
            broker.stop();
        }
    }

    @Test
    void failsWithAJmsExceptionWhenTheBrokerIsUnreachable() {
        EquipmentAlertPublisher publisher = new EquipmentAlertPublisher("tcp://localhost:1", QUEUE);

        // an alert that could not be queued must never look as if it was
        assertThrows(JMSException.class, () -> publisher.publish(
                new EquipmentFailureAlert("a-3", "W-05", "pump", null, 1L)));
    }
}