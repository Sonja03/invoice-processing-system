package net.etfbl.mdp.approvalclient;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Faza 5 - pokretanje GUI klijenta za odobravanje faktura.
 *
 * Spaja dva svijeta: JMS niti koje primaju poruke i Swing nit
 * koja jedina smije dirati komponente.
 */
public class ApprovalClientMain {

    private static final Logger log = LoggerFactory.getLogger(ApprovalClientMain.class);

    private static final String TOPIC_PENDING = "invoices.pending_approval";

    public static void main(String[] args) {

        // Prozor se pravi na Swing niti (EDT), nikad na main niti.
        SwingUtilities.invokeLater(() -> {
            ApprovalClientUI ui = new ApprovalClientUI();
            ui.setVisible(true);

            try {
                MqClient.pokreni();
                MqClient.pretplati(TOPIC_PENDING, new PendingListener(ui));

                log.info("Approval klijent spreman, slusa {}", TOPIC_PENDING);

            } catch (Exception e) {
                log.error("Nije moguce povezati se na ActiveMQ", e);
                JOptionPane.showMessageDialog(ui,
                        "Nije moguce povezati se na ActiveMQ.\nProvjeri da li Docker radi.",
                        "Greska",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Gasim Approval klijent...");
            MqClient.zaustavi();
        }, "shutdown-hook"));
    }

    /**
     * Prima fakture sa invoices.pending_approval.
     *
     * onMessage se izvrsava na JMS niti, a tabelu smije mijenjati samo
     * Swing nit - zato invokeLater.
     */
    private static class PendingListener implements MessageListener {

        private final ApprovalClientUI ui;

        PendingListener(ApprovalClientUI ui) {
            this.ui = ui;
        }

        @Override
        public void onMessage(Message message) {
            try {
                if (!(message instanceof TextMessage tm)) {
                    log.error("Poruka nije TextMessage, preskacem");
                    return;
                }

                Invoice faktura = JsonUtil.fromJson(tm.getText(), Invoice.class);

                log.info("Stigao zahtjev za odobrenje: {} ({} BAM)",
                        faktura.getId(), faktura.getTotalBAM());

                // prelazak sa JMS niti na Swing nit
                SwingUtilities.invokeLater(() -> ui.dodajFakturu(faktura));

            } catch (Exception e) {
                log.error("Greska pri prijemu zahtjeva za odobrenje", e);
            }
        }
    }
}