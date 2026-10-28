package net.etfbl.mdp.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Faza 5 - pokretanje Approval servisa.
 *
 * Mora se pokrenuti PRIJE Enrichmenta i Parsera. Kod topica poruka
 * ide samo onima koji su pretplaceni u trenutku slanja.
 */
public class ApprovalMain {

    private static final Logger log = LoggerFactory.getLogger(ApprovalMain.class);

    private static final String TOPIC_ENRICHED = "invoices.enriched";

    public static void main(String[] args) {
        try {
            MqClient.pokreni();

            MqClient.pretplati(TOPIC_ENRICHED, new ApprovalListener());

            log.info("Approval servis spreman, prag za automatsko odobrenje je 100 BAM");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Gasim Approval servis...");
                MqClient.zaustavi();
            }, "shutdown-hook"));

        } catch (Exception e) {
            log.error("Approval ne moze da se pokrene - provjeri Docker", e);
            System.exit(1);
        }
    }
}