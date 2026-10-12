package co.wethinkcode.healthsafe;

import java.util.concurrent.CountDownLatch;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Consumes equipment-failure-queue, guaranteeing that no alert is lost.

 * How the guarantee works:
 *  - the session is TRANSACTED: a message is only removed from the queue when we commit, which we
 *    do AFTER the handler has finished. If we crash or the handler throws, the message stays on
 *    the broker and is delivered again (to us, or to another consumer);
 *  - a message that keeps failing is eventually moved by the broker to its dead-letter queue
 *    (ActiveMQ.DLQ by default) where a human can inspect it, instead of being dropped;
 *  - alerts sent while this service is down wait on the queue until a consumer connects.

 * Like the topic subscriber in ward-service, it connects on a background thread and retries, so the
 * service starts and answers HTTP even while the broker is down.
 */
public final class EquipmentAlertConsumer {

    private final String brokerUrl;
    private final String queueName;
    private final AlertHandler handler;
    private final long retryDelayMillis;

    private volatile boolean stopped;
    private volatile boolean consuming;
    private Thread thread;

    public EquipmentAlertConsumer(String brokerUrl, String queueName,
                                  AlertHandler handler, long retryDelayMillis) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.handler = handler;
        this.retryDelayMillis = retryDelayMillis;
    }

    // Returns immediately; connecting happens on the background thread.
    public void start() {
        thread = new Thread(this::runLoop, "equipment-alert-consumer");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        stopped = true;
        if (thread != null) {
            thread.interrupt();
        }
    }

    // True only while connected and ready to receive alerts. */
    public boolean isConsuming() {
        return consuming;
    }

    private void runLoop() {
        while (!stopped) {
            Connection connection = null;
            try {
                CountDownLatch connectionLost = new CountDownLatch(1);
                connection = new ActiveMQConnectionFactory(brokerUrl).createConnection();
                connection.setExceptionListener(e -> connectionLost.countDown());
                Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
                consumer.setMessageListener(message -> onMessage(session, message));
                connection.start();
                consuming = true;
                System.out.println("Consuming queue " + queueName + " at " + brokerUrl);
                connectionLost.await();
                System.err.println("Lost the connection to the message broker");
            } catch (JMSException e) {
                System.err.println("Cannot consume " + queueName + ": " + e);
            } catch (InterruptedException e) {
                return;
            } finally {
                consuming = false;
                closeQuietly(connection);
            }
            if (!pause()) {
                return;
            }
        }
    }

    // Runs on the session's thread, so it may commit or roll back that session.
    private void onMessage(Session session, Message message) {
        try {
            if (!(message instanceof TextMessage text)) {
                throw new IllegalArgumentException("not a text message");
            }
            handler.handle(EquipmentAlertParser.parse(text.getText()));
            session.commit(); // only now does the broker forget the alert
        } catch (Exception e) {
            System.err.println("Alert NOT handled, it will be redelivered: " + e);
            rollbackQuietly(session);
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(retryDelayMillis);
            return !stopped;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private static void rollbackQuietly(Session session) {
        try {
            session.rollback();
        } catch (JMSException e) {
            System.err.println("Rollback failed: " + e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (JMSException ignored) {
            // already broken
        }
    }
}