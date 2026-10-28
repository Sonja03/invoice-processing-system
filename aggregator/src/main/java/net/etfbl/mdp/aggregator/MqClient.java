package net.etfbl.mdp.aggregator;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;

/**
 * Veza sa ActiveMQ brokerom.
 *
 * Aggregator je jedini servis koji SAMO prima - nikad ne salje na MQ.
 * Zato ova klasa nema metodu posalji().
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

    /**
     * Pretplacuje listener na topic.
     *
     * Sesija i consumer se NE zatvaraju - moraju ostati otvoreni
     * dokle god servis radi.
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