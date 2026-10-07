package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

class ScheduleEventPublisherTest {

    // vm:// starts a private broker inside this JVM; no Docker needed
    private static final String IN_MEMORY_BROKER =
            "vm://localhost?broker.persistent=false&broker.useJmx=false";
    private static final String TOPIC = "test-staffing-events";

    private final ScheduleUpdatedEvent event =
            new ScheduleUpdatedEvent("ScheduleUpdated", "W-05", 8, "CODE_BLUE", 9, 1791360000000L);

    @Test
    void publishesTheEventAsJsonOnTheTopic() throws Exception {
        Connection connection = new ActiveMQConnectionFactory(IN_MEMORY_BROKER).createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            // subscribe BEFORE publishing: a topic does not keep messages for late subscribers
            MessageConsumer consumer = session.createConsumer(session.createTopic(TOPIC));

            new ScheduleEventPublisher(IN_MEMORY_BROKER, TOPIC).publish(event);

            Message received = consumer.receive(5000);
            assertNotNull(received, "no message arrived within 5 seconds");
            JsonNode json = new ObjectMapper().readTree(((TextMessage) received).getText());
            assertEquals("ScheduleUpdated", json.get("type").asText());
            assertEquals("W-05", json.get("wardId").asText());
            assertEquals(9, json.get("doctorsOnCall").asInt());
        } finally {
            connection.close();
        }
    }

    @Test
    void failsWithAJmsExceptionWhenTheBrokerIsUnreachable() {
        // nothing listens on port 1
        ScheduleEventPublisher publisher = new ScheduleEventPublisher("tcp://localhost:1", TOPIC);

        assertThrows(JMSException.class, () -> publisher.publish(event));
    }
}