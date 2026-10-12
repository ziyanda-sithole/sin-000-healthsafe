package co.wethinkcode.healthsafe;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Listens on the staffing topic and files every valid ScheduleUpdated event in the store.

 * Runs on its own background thread and keeps (re)trying to connect, so:
 *  - ward-service starts and serves wards even if the broker is down;
 *  - if the broker dies or restarts later, the subscription comes back by itself.
 * While disconnected, events published in the meantime are lost: a topic does not remember
 * them for absent subscribers. That is accepted because each event carries the full state.
 */
public final class StaffingEventSubscriber {

    private final String brokerUrl;
    private final String topicName;
    private final StaffingStore store;
    private final long retryDelayMillis;

    private volatile boolean stopped;
    private volatile boolean subscribed;
    private Thread thread;

    public StaffingEventSubscriber(String brokerUrl, String topicName,
                                   StaffingStore store, long retryDelayMillis) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
        this.store = store;
        this.retryDelayMillis = retryDelayMillis;
    }

    // Returns immediately; connecting happens on the background thread.
    public void start() {
        thread = new Thread(this::runLoop, "staffing-event-subscriber");
        thread.setDaemon(true); // never keeps the JVM alive on its own
        thread.start();
    }

    public void stop() {
        stopped = true;
        if (thread != null) {
            thread.interrupt();
        }
    }

    // True only while connected and listening.
    public boolean isSubscribed() {
        return subscribed;
    }

    private void runLoop() {
        while (!stopped) {
            Connection connection = null;
            try {
                CountDownLatch connectionLost = new CountDownLatch(1);
                connection = new ActiveMQConnectionFactory(brokerUrl).createConnection();
                connection.setExceptionListener(e -> connectionLost.countDown());
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(session.createTopic(topicName));
                consumer.setMessageListener(this::onMessage);
                connection.start(); // messages start flowing only now
                subscribed = true;
                System.out.println("Subscribed to topic " + topicName + " at " + brokerUrl);
                connectionLost.await(); // sleep until the broker connection fails
                System.err.println("Lost the connection to the message broker");
            } catch (JMSException e) {
                System.err.println("Cannot subscribe to " + topicName + ": " + e);
            } catch (InterruptedException e) {
                return; // stop() was called
            } finally {
                subscribed = false;
                closeQuietly(connection);
            }
            if (!pause()) {
                return;
            }
        }
    }

    // Called on a broker thread for every message. It must never throw.
    private void onMessage(Message message) {
        try {
            if (!(message instanceof TextMessage text)) {
                System.err.println("Ignoring a message that is not text");
                return;
            }
            Optional<ScheduleUpdatedEvent> event = ScheduleEventParser.parse(text.getText());
            if (event.isPresent()) {
                ScheduleUpdatedEvent e = event.get();
                store.accept(e);
                System.out.println("Staffing update for " + e.wardId() + ": level " + e.alertLevel()
                        + " " + e.status() + ", " + e.doctorsOnCall() + " doctors on call");
            }
        } catch (Exception e) {
            // A message we cannot read must not kill the listener or be redelivered forever.
            System.err.println("Ignoring unreadable staffing message: " + e);
        }
    }

    // Waits before reconnecting. Returns false if we should stop instead.
    private boolean pause() {
        try {
            Thread.sleep(retryDelayMillis);
            return !stopped;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (JMSException ignored) {
            // already broken; nothing useful to do
        }
    }
}