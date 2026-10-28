package net.etfbl.mdp.approval;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Faza 5 - Approval servis.
 *
 * Slusa invoices.enriched. Fakture do 100 BAM odobrava automatski,
 * ostale salje na invoices.pending_approval gdje ih preuzima GUI klijent.
 */
public class ApprovalListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ApprovalListener.class);

    public static final String TOPIC_APPROVED = "invoices.approved";
    public static final String TOPIC_PENDING = "invoices.pending_approval";

    /** Zadatak: totalBAM <= 100 se odobrava automatski. */
    private static final BigDecimal PRAG = new BigDecimal("100");

    private static final String AUTO_ODOBRENJE = "AUTO";

    @Override
    public void onMessage(Message message) {
        String id = "?";

        try {
            // === 1. poruka -> Invoice ===
            if (!(message instanceof TextMessage tm)) {
                log.error("Poruka nije TextMessage, preskacem");
                return;
            }

            Invoice faktura = JsonUtil.fromJson(tm.getText(), Invoice.class);
            id = faktura.getId();

            // === 2. provjera da li uopste mozemo odluciti ===
            BigDecimal totalBAM = faktura.getTotalBAM();

            if (totalBAM == null) {
                log.error("Faktura {} nema totalBAM, ne mogu odluciti", id);
                return;
            }

            log.info("Primljena faktura {} ({} BAM, klijent {})",
                    id, totalBAM,
                    faktura.getClient() == null ? "?" : faktura.getClient().getName());

            // === 3. automatsko odobrenje ===
            if (totalBAM.compareTo(PRAG) <= 0) {
                faktura.setStatus("approved");
                faktura.setApprovedBy(AUTO_ODOBRENJE);
                faktura.setDecisionTimestamp(LocalDateTime.now().toString());

                MqClient.posalji(TOPIC_APPROVED, JsonUtil.toJson(faktura));

                log.info("Faktura {} AUTOMATSKI ODOBRENA ({} <= {} BAM) -> {}",
                        id, totalBAM, PRAG, TOPIC_APPROVED);
                return;
            }

            // === 4. ceka rucno odobrenje ===
            faktura.setStatus("pending");

            MqClient.posalji(TOPIC_PENDING, JsonUtil.toJson(faktura));

            log.info("Faktura {} CEKA ODOBRENJE ({} > {} BAM) -> {}",
                    id, totalBAM, PRAG, TOPIC_PENDING);

        } catch (Exception e) {
            log.error("Greska pri obradi fakture {}", id, e);
        }
    }
}