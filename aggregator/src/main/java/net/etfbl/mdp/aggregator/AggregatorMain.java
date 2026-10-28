package net.etfbl.mdp.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Faza 6 - pokretanje Aggregator servisa.
 *
 * Pretplacuje se na pet topica. Svaki dobija svoj listener sa
 * svojim statusom i podatkom da li ulazi u statistiku klijenta.
 *
 * Mora se pokrenuti PRIJE svih servisa koji salju poruke.
 */
public class AggregatorMain {

    private static final Logger log = LoggerFactory.getLogger(AggregatorMain.class);

    private static final String TOPIC_APPROVED = "invoices.approved";
    private static final String TOPIC_REJECTED = "invoices.rejected";
    private static final String TOPIC_FAILED = "invoices.failed";
    private static final String TOPIC_PENDING = "invoices.pending_approval";
    private static final String TOPIC_ENRICHED = "invoices.enriched";

    public static void main(String[] args) {
        try {
            RedisStore.provjeriVezu();
            MulticastSender.pokreni();
            MqClient.pokreni();

            // samo odobrene ulaze u statistiku klijenta i idu na multicast
            MqClient.pretplati(TOPIC_APPROVED,
                    new InvoiceAggregator(TOPIC_APPROVED, "approved", true));

            MqClient.pretplati(TOPIC_REJECTED,
                    new InvoiceAggregator(TOPIC_REJECTED, "rejected", false));

            MqClient.pretplati(TOPIC_FAILED,
                    new InvoiceAggregator(TOPIC_FAILED, "failed", false));

            MqClient.pretplati(TOPIC_PENDING,
                    new InvoiceAggregator(TOPIC_PENDING, "pending", false));

            MqClient.pretplati(TOPIC_ENRICHED,
                    new InvoiceAggregator(TOPIC_ENRICHED, "enriched", false));

            log.info("Aggregator spreman, prati 5 topica");
            log.info("Multicast notifikacije idu na {}:{}",
                    MulticastSender.GRUPA, MulticastSender.PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Gasim Aggregator...");
                MulticastSender.zaustavi();
                MqClient.zaustavi();
            }, "shutdown-hook"));

        } catch (Exception e) {
            log.error("Aggregator ne moze da se pokrene - provjeri Docker", e);
            System.exit(1);
        }
    }
}