package net.etfbl.mdp.aggregator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Faza 6 - Aggregator.
 *
 * Jedan primjerak ove klase se pravi po topicu, pa svaki zna
 * odakle mu poruke dolaze i koji status treba upisati.
 *
 * Za svaku fakturu: upis u Redis.
 * Samo za odobrene: statistika klijenta + UDP multicast.
 */
public class InvoiceAggregator implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAggregator.class);

    /** Naziv topica sa kojeg ovaj listener prima poruke. */
    private final String topic;

    /** Status koji se upisuje fakturama sa ovog topica. */
    private final String status;

    /** Da li fakture sa ovog topica ulaze u statistiku i multicast. */
    private final boolean odobrene;

    public InvoiceAggregator(String topic, String status, boolean odobrene) {
        this.topic = topic;
        this.status = status;
        this.odobrene = odobrene;
    }

    @Override
    public void onMessage(Message message) {
        String id = "?";

        try {
            if (!(message instanceof TextMessage tm)) {
                log.error("Poruka sa {} nije TextMessage, preskacem", topic);
                return;
            }

            Invoice faktura = izvuciFakturu(tm.getText());

            if (faktura == null || faktura.getId() == null) {
                log.error("Poruka sa {} ne sadrzi ispravnu fakturu", topic);
                return;
            }

            id = faktura.getId();

            // status iz topica je mjerodavan
            faktura.setStatus(status);

            log.info("[{}] faktura {} ({} BAM)", topic, id, faktura.getTotalBAM());

            // === za SVAKU fakturu ===
            RedisStore.sacuvajFakturu(faktura);

            // === samo za odobrene ===
            if (odobrene) {
                RedisStore.azurirajKlijenta(faktura);
                MulticastSender.posalji(notifikacija(faktura));
            }

        } catch (Exception e) {
            log.error("Greska pri obradi poruke sa {} (faktura {})", topic, id, e);
        }
    }

    /**
     * Poruke sa invoices.rejected koje salje Parser imaju drugaciji oblik:
     *   {"invoice": {...}, "errors": [...], "validatorTimestamp": "..."}
     *
     * Sve ostale su faktura direktno. Zato se prvo provjeri oblik.
     */
    private Invoice izvuciFakturu(String json) throws Exception {
        JsonNode root = JsonUtil.mapper().readTree(json);

        if (root.has("invoice")) {
            Invoice faktura = JsonUtil.mapper()
                    .treeToValue(root.path("invoice"), Invoice.class);

            JsonNode errors = root.path("errors");
            if (!errors.isMissingNode() && errors.isArray() && errors.size() > 0) {
                StringBuilder sb = new StringBuilder();
                errors.forEach(e -> sb.append(sb.length() == 0 ? "" : "; ").append(e.asText()));
                faktura.setRejectionReason(sb.toString());
            }

            return faktura;
        }

        return JsonUtil.mapper().treeToValue(root, Invoice.class);
    }

    /**
     * Format notifikacije propisan zadatkom:
     * {invoiceId, clientName, totalBAM, status, timestamp}
     */
    private String notifikacija(Invoice faktura) {
        Map<String, Object> poruka = new LinkedHashMap<>();

        poruka.put("invoiceId", faktura.getId());
        poruka.put("clientName",
                faktura.getClient() == null ? "?" : faktura.getClient().getName());
        poruka.put("totalBAM",
                faktura.getTotalBAM() == null ? BigDecimal.ZERO : faktura.getTotalBAM());
        poruka.put("status", faktura.getStatus());
        poruka.put("timestamp", LocalDateTime.now().toString());

        return JsonUtil.toJson(poruka);
    }
}