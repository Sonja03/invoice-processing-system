package net.etfbl.mdp.enrichment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Faza 4b - retry mehanizam.
 *
 * Slusa invoices.retry. Za svaku fakturu ceka N sekundi pa pokusava
 * ponovo dohvatiti kurs. Poslije MAX_POKUSAJA odustaje.
 *
 * Cekanje ide preko ScheduledExecutorService-a, ne preko Thread.sleep -
 * sleep bi blokirao JMS nit i zaustavio prijem ostalih poruka.
 */
public class RetryListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RetryListener.class);

    public static final String TOPIC_ENRICHED = "invoices.enriched";
    public static final String TOPIC_RETRY = "invoices.retry";
    public static final String TOPIC_FAILED = "invoices.failed";

    /** Koliko se ceka prije ponovnog pokusaja. */
    private static final int PAUZA_SEKUNDI = 30;

    /** Poslije ovoliko pokusaja se odustaje. */
    private static final int MAX_POKUSAJA = 3;

    private static final String RAZLOG_ODUSTAJANJA = "RATE_UNAVAILABLE";

    private static final int SKALA_NOVCA = 2;

    /**
     * Nekoliko niti koje izvrsavaju odlozene pokusaje.
     * Vise od jedne da jedan spor pokusaj ne blokira ostale.
     */
    private final ScheduledExecutorService raspored;

    public RetryListener() {
        AtomicInteger brojac = new AtomicInteger(1);

        this.raspored = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "retry-" + brojac.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (!(message instanceof TextMessage tm)) {
                log.error("Poruka na retry topicu nije TextMessage, preskacem");
                return;
            }

            Invoice faktura = JsonUtil.fromJson(tm.getText(), Invoice.class);

            log.info("Faktura {} primljena na retry (dosad pokusaja: {}), cekam {}s",
                    faktura.getId(), faktura.getRetryCount(), PAUZA_SEKUNDI);

            // Ne blokiramo JMS nit - zakazujemo pokusaj za kasnije.
            raspored.schedule(() -> pokusajPonovo(faktura),
                    PAUZA_SEKUNDI, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Greska pri prijemu retry poruke", e);
        }
    }

    /**
     * Izvrsava se N sekundi kasnije, na retry niti.
     */
    private void pokusajPonovo(Invoice faktura) {
        String id = faktura.getId();

        try {
            int pokusaj = faktura.getRetryCount() + 1;
            log.info("Pokusaj {} za fakturu {} ({})", pokusaj, id, faktura.getCurrency());

            RateProvider.Kurs kurs = RateProvider.dohvatiKurs(faktura.getCurrency());

            // --- uspjelo ---
            if (kurs != null) {
                BigDecimal totalBAM = faktura.getTotal()
                        .multiply(kurs.vrijednost())
                        .setScale(SKALA_NOVCA, RoundingMode.HALF_UP);

                faktura.setTotalBAM(totalBAM);
                faktura.setRate(kurs.vrijednost());
                faktura.setRateSource(kurs.izvor());
                faktura.setStatus("enriched");
                faktura.setRetryCount(pokusaj);

                MqClient.posalji(TOPIC_ENRICHED, JsonUtil.toJson(faktura));

                log.info("Faktura {} uspjela iz {}. pokusaja: {} BAM",
                        id, pokusaj, totalBAM);
                return;
            }

            // --- nije uspjelo, iscrpljeni pokusaji ---
            if (pokusaj >= MAX_POKUSAJA) {
                faktura.setRetryCount(pokusaj);
                faktura.setStatus("failed");
                faktura.setRejectionReason(RAZLOG_ODUSTAJANJA);

                MqClient.posalji(TOPIC_FAILED, JsonUtil.toJson(faktura));

                log.error("Faktura {} odustaje poslije {} pokusaja -> {} ({})",
                        id, pokusaj, TOPIC_FAILED, RAZLOG_ODUSTAJANJA);
                return;
            }

            // --- nije uspjelo, ima jos pokusaja ---
            faktura.setRetryCount(pokusaj);
            faktura.setStatus("retry");

            MqClient.posalji(TOPIC_RETRY, JsonUtil.toJson(faktura));

            log.warn("Faktura {} nije uspjela iz {}. pokusaja, vracam na {}",
                    id, pokusaj, TOPIC_RETRY);

        } catch (Exception e) {
            log.error("Greska pri ponovnom pokusaju za fakturu {}", id, e);
        }
    }
}