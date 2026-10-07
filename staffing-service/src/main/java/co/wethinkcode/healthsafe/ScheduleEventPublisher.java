package co.wethinkcode.healthsafe;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Publishes schedule events to the staffing topic.

 * A fresh connection is opened for each event and closed straight after. That is slower than
 * keeping one open, but publishing is rare (an explicit POST), and it means:
 *  - staffing-service starts and serves GET /schedule even when the broker is down, so stage 3
 *    can never break the core;
 *  - there is no long-lived connection to go stale if the broker restarts;
 *  - there is no shared JMS Session to protect, because a Session must only be used by one
 *    thread at a time and Javalin handles requests on many.
 */
public final class ScheduleEventPublisher {

    private final ActiveMQConnectionFactory factory;
    private final String topicName;

    public ScheduleEventPublisher(String brokerUrl, String topicName) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
        this.topicName = topicName;
    }

    // Sends the event as a JSON text message. Throws JMSException if the broker cannot be used.
    public void publish(ScheduleUpdatedEvent event) throws JMSException {
        Connection connection = factory.createConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);
            MessageProducer producer = session.createProducer(topic);
            TextMessage message = session.createTextMessage(ScheduleEventCodec.toJson(event));
            producer.send(message);
        } finally {
            connection.close();
        }
    }
}