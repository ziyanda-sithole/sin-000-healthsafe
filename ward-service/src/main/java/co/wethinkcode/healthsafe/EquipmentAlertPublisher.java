package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Queues an equipment failure alert for equipment-alert-service.

 * Messages are PERSISTENT: the broker stores them before acknowledging, and send() waits for that
 * acknowledgement. So when publish() returns without an exception, the broker has the alert; if it
 * throws, the alert was NOT sent and the caller must not pretend otherwise.

 * A connection is opened per alert, so ward-service starts and serves wards even if the broker is down.
 */
public final class EquipmentAlertPublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActiveMQConnectionFactory factory;
    private final String queueName;

    public EquipmentAlertPublisher(String brokerUrl, String queueName) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
        this.queueName = queueName;
    }

    public void publish(EquipmentFailureAlert alert) throws JMSException {
        String json = toJson(alert);
        Connection connection = factory.createConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(queueName));
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(session.createTextMessage(json));
        } finally {
            connection.close();
        }
    }

    private static String toJson(EquipmentFailureAlert alert) {
        try {
            return MAPPER.writeValueAsString(alert);
        } catch (JsonProcessingException e) {
            // A record of strings and numbers cannot fail to serialise: this would be a bug.
            throw new IllegalStateException("Could not serialise " + alert, e);
        }
    }
}