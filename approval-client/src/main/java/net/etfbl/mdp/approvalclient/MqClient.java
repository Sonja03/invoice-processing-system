package net.etfbl.mdp.approvalclient;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

/**
 * Veza sa ActiveMQ brokerom za GUI klijent.
 *
 * Klijent slusa invoices.pending_approval i salje odluke
 * na invoices.approved ili invoices.rejected.
 */
public class MqClient {

    private static final Logger log = LoggerFactory.getLogger(MqClient.class);

    private static final String BROKER_URL = "tcp://localhost:61616";

    private static Connection connection;

    private MqClient() {
    }

    public static void pokreni() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        factory.setTrustAllPackages(true);

        connection = factory.createConnection();
        connection.start();

        log.info("Povezan na ActiveMQ {}", BROKER_URL);
    }

    public static void posalji(String topicName, String poruka) {
        Session session = null;
        MessageProducer producer = null;

        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);
            producer = session.createProducer(topic);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            producer.send(session.createTextMessage(poruka));

            log.info("Poslato na topic {} ({} znakova)", topicName, poruka.length());

        } catch (Exception e) {
            log.error("Greska pri slanju na topic {}", topicName, e);

        } finally {
            try {
                if (producer != null) {
                    producer.close();
                }
                if (session != null) {
                    session.close();
                }
            } catch (Exception e) {
                log.warn("Greska pri zatvaranju JMS resursa", e);
            }
        }
    }

    public static void pretplati(String topicName, MessageListener listener) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(topicName);
        MessageConsumer consumer = session.createConsumer(topic);

        consumer.setMessageListener(listener);

        log.info("Pretplacen na topic {}", topicName);
    }

    public static void zaustavi() {
        try {
            if (connection != null) {
                connection.close();
                log.info("ActiveMQ konekcija zatvorena");
            }
        } catch (Exception e) {
            log.warn("Greska pri zatvaranju konekcije", e);
        }
    }
}