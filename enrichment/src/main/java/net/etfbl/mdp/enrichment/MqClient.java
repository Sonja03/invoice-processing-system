package net.etfbl.mdp.enrichment;

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
 * Veza sa ActiveMQ brokerom - i slanje i primanje.
 *
 * Konekcija se otvara jednom pri pokretanju. Sesija se pravi
 * po pozivu jer JMS sesija nije sigurna za vise niti.
 */
public class MqClient {

    private static final Logger log = LoggerFactory.getLogger(MqClient.class);

    private static final String BROKER_URL = "tcp://localhost:61616";

    private static Connection connection;

    private MqClient() {
    }

    /** Poziva se jednom, pri pokretanju servisa. */
    public static void pokreni() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        factory.setTrustAllPackages(true);

        connection = factory.createConnection();
        connection.start();

        log.info("Povezan na ActiveMQ {}", BROKER_URL);
    }

    // ================= SLANJE =================

    /**
     * Salje tekstualnu poruku na zadati topic.
     * Sesija i producer se prave i zatvaraju po pozivu.
     */
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

    // ================= PRIMANJE =================

    /**
     * Pretplacuje listener na topic.
     *
     * Sesija i consumer se NE zatvaraju - moraju ostati otvoreni
     * dokle god servis radi, inace poruke ne bi stizale.
     */
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