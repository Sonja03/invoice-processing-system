package net.etfbl.mdp.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Faza 4 i 4b - pokretanje Enrichment servisa.
 *
 * Mora se pokrenuti PRIJE Parsera. Kod topica poruka ide samo onima
 * koji su pretplaceni u trenutku slanja - ako niko ne slusa, nestaje.
 */
public class EnrichmentMain {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentMain.class);

    private static final String TOPIC_VALIDATED = "invoices.validated";
    private static final String TOPIC_RETRY = "invoices.retry";

    public static void main(String[] args) {
        try {
            MqClient.pokreni();

            // Faza 4 - glavni tok
            MqClient.pretplati(TOPIC_VALIDATED, new InvoiceEnricher());

            // Faza 4b - retry mehanizam
            MqClient.pretplati(TOPIC_RETRY, new RetryListener());

            log.info("Enrichment servis spreman, ceka fakture...");

            // Uredno gasenje na Ctrl+C ili zaustavljanje iz Eclipsea
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Gasim Enrichment servis...");
                MqClient.zaustavi();
            }, "shutdown-hook"));

        } catch (Exception e) {
            log.error("Enrichment ne moze da se pokrene - provjeri Docker", e);
            System.exit(1);
        }
    }
}