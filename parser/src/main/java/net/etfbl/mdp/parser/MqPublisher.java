package net.etfbl.mdp.parser;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

/**
 * Slanje poruka na ActiveMQ topice (Faza 2).
 *
 * Konekcija se otvara jednom pri pokretanju i dijele je sve radne niti.
 * Sesija se pravi po pozivu jer JMS sesija nije sigurna za vise niti.
 */
public class MqPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqPublisher.class);

    private static final String BROKER_URL = "tcp://localhost:61616";

    private static Connection connection;

    private MqPublisher() {
    }

    /** Poziva se jednom, pri pokretanju Parsera. */
    public static void pokreni() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);

        // bez ovoga ActiveMQ odbija deserijalizaciju nepoznatih klasa
        factory.setTrustAllPackages(true);

        connection = factory.createConnection();
        connection.start();

        log.info("Povezan na ActiveMQ {}", BROKER_URL);
    }

    /**
     * Salje tekstualnu poruku na zadati topic.
     */
    public static void posalji(String topicName, String poruka) {
        Session session = null;
        MessageProducer producer = null;

        try {
            // false = bez transakcija, AUTO_ACKNOWLEDGE = broker sam potvrdjuje
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(topicName);
            producer = session.createProducer(topic);

            // poruke prezivljavaju restart brokera
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            producer.send(session.createTextMessage(poruka));

            log.info("Poslato na topic {} ({} znakova)", topicName, poruka.length());

        } catch (Exception e) {
            log.error("Greska pri slanju na topic {}", topicName, e);
        } finally {
            zatvori(producer, session);
        }
    }

    private static void zatvori(MessageProducer producer, Session session) {
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

    /** Poziva se pri gasenju servisa. */
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