package net.etfbl.mdp.enrichment;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Faza 4 - obogacivanje fakture kursom.
 *
 * Slusa invoices.validated. Za svaku fakturu dohvata kurs,
 * racuna totalBAM i salje dalje na invoices.enriched.
 *
 * Ako kurs nije dostupan, faktura ide na invoices.retry.
 */
public class InvoiceEnricher implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEnricher.class);

    public static final String TOPIC_ENRICHED = "invoices.enriched";
    public static final String TOPIC_RETRY = "invoices.retry";

    /** Novcani iznosi se zaokruzuju na dvije decimale. */
    private static final int SKALA_NOVCA = 2;

    @Override
    public void onMessage(Message message) {
        String id = "?";

        try {
            // === 1. poruka -> Invoice objekat ===
            if (!(message instanceof TextMessage tm)) {
                log.error("Primljena poruka nije TextMessage, preskacem");
                return;
            }

            String json = tm.getText();
            Invoice faktura = JsonUtil.fromJson(json, Invoice.class);
            id = faktura.getId();

            log.info("Primljena faktura {} ({} {})", id, faktura.getTotal(), faktura.getCurrency());

            // === 2. kurs ===
            RateProvider.Kurs kurs = RateProvider.dohvatiKurs(faktura.getCurrency());

            if (kurs == null) {
                log.warn("Kurs za {} nije dostupan, faktura {} ide na retry",
                        faktura.getCurrency(), id);
                posaljiNaRetry(faktura);
                return;
            }

            // === 3. racunanje totalBAM ===
            obogati(faktura, kurs);

            // === 4. dalje u lanac ===
            MqClient.posalji(TOPIC_ENRICHED, JsonUtil.toJson(faktura));

            log.info("Faktura {} obogacena: {} {} -> {} BAM (kurs {}, izvor {})",
                    id, faktura.getTotal(), faktura.getCurrency(),
                    faktura.getTotalBAM(), kurs.vrijednost(), kurs.izvor());

        } catch (Exception e) {
            // onMessage NIKAD ne smije pustiti izuzetak napolje - JMS bi
            // poruku isporucio ponovo i vrtjela bi se u krug
            log.error("Greska pri obradi fakture {}", id, e);
        }
    }

    /**
     * Popunjava totalBAM, rate i rateSource.
     * Ta polja su u Invoice stajala prazna od Parsera.
     */
    private void obogati(Invoice faktura, RateProvider.Kurs kurs) {
        BigDecimal totalBAM = faktura.getTotal()
                .multiply(kurs.vrijednost())
                .setScale(SKALA_NOVCA, RoundingMode.HALF_UP);

        faktura.setTotalBAM(totalBAM);
        faktura.setRate(kurs.vrijednost());
        faktura.setRateSource(kurs.izvor());
        faktura.setStatus("enriched");
    }

    /**
     * Prvi neuspjeh - brojac ide na 1.
     */
    private void posaljiNaRetry(Invoice faktura) {
        faktura.setRetryCount(1);
        faktura.setStatus("retry");
        MqClient.posalji(TOPIC_RETRY, JsonUtil.toJson(faktura));
        log.info("Faktura {} poslata na {} (pokusaj 1)", faktura.getId(), TOPIC_RETRY);
    }
}