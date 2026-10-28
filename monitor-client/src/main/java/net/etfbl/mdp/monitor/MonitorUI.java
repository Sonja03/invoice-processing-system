package net.etfbl.mdp.monitor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.math.BigDecimal;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Monitor klijent - prikazuje odobrene fakture u realnom vremenu.
 *
 * Podaci stizu UDP multicastom, u formatu:
 *   {invoiceId, clientName, totalBAM, status, timestamp}
 *
 * Sve metode ove klase smiju se pozivati SAMO sa Swing niti (EDT).
 */
public class MonitorUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(MonitorUI.class);

    /** Koliko redova cuvamo u tabeli prije nego pocnemo brisati najstarije. */
    private static final int MAX_REDOVA = 500;

    private final DefaultTableModel model;
    private final JTable tabela;
    private final JLabel statusna;

    private int brojPrimljenih = 0;
    private BigDecimal ukupnoBAM = BigDecimal.ZERO;

    public MonitorUI(String naslov) {
        super(naslov);

        String[] kolone = {"Vrijeme", "ID fakture", "Klijent", "Iznos BAM", "Status"};

        model = new DefaultTableModel(kolone, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabela = new JTable(model);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setRowHeight(24);
        tabela.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        statusna = new JLabel(" Cekam notifikacije...");
        statusna.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        setLayout(new BorderLayout());
        add(statusna, BorderLayout.NORTH);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(900, 500));
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Dodaje jednu notifikaciju u tabelu.
     * MORA se pozvati sa Swing niti - vidi MonitorMain.
     */
    public void dodajNotifikaciju(String json) {
        try {
            JsonNode n = JsonUtil.mapper().readTree(json);

            String invoiceId = n.path("invoiceId").asText("?");
            String clientName = n.path("clientName").asText("?");
            String totalBAM = n.path("totalBAM").asText("0");
            String status = n.path("status").asText("?");
            String timestamp = n.path("timestamp").asText("");

            model.insertRow(0, new Object[]{
                    skratiVrijeme(timestamp),
                    invoiceId,
                    clientName,
                    totalBAM,
                    status
            });

            // najstarije redove izbacujemo da tabela ne raste bez kraja
            while (model.getRowCount() > MAX_REDOVA) {
                model.removeRow(model.getRowCount() - 1);
            }

            brojPrimljenih++;
            ukupnoBAM = ukupnoBAM.add(new BigDecimal(totalBAM));

            statusna.setText(String.format(
                    " Primljeno notifikacija: %d    |    Ukupno: %s BAM",
                    brojPrimljenih, ukupnoBAM));

            log.info("U tabeli: {} ({} BAM)", invoiceId, totalBAM);

        } catch (Exception e) {
            log.error("Neispravna notifikacija: {}", json, e);
        }
    }

    /** Iz "2026-09-11T12:05:33.421" pravi "12:05:33". */
    private String skratiVrijeme(String timestamp) {
        int t = timestamp.indexOf('T');
        if (t < 0) {
            return timestamp;
        }
        String vrijeme = timestamp.substring(t + 1);
        int tacka = vrijeme.indexOf('.');
        return tacka < 0 ? vrijeme : vrijeme.substring(0, tacka);
    }
}