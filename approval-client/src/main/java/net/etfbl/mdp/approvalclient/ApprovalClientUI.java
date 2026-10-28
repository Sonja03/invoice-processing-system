package net.etfbl.mdp.approvalclient;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.model.Item;
import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Faza 5 - GUI klijent za rucno odobravanje faktura.
 *
 * Sve metode ove klase smiju se pozivati SAMO sa Swing niti (EDT).
 * JMS nit koja prima poruke mora koristiti SwingUtilities.invokeLater.
 */
public class ApprovalClientUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ApprovalClientUI.class);

    public static final String TOPIC_APPROVED = "invoices.approved";
    public static final String TOPIC_REJECTED = "invoices.rejected";

    /** Ime korisnika koji odlucuje - ide u polje approvedBy. */
    private static final String KORISNIK = "operater";

    /** Fakture koje cekaju, redoslijed isti kao u tabeli. */
    private final List<Invoice> fakture = new ArrayList<>();

    private final DefaultTableModel model;
    private final JTable tabela;
    private final JTextArea detalji;
    private final JTextField poljeRazlog;
    private final JButton dugmeOdobri;
    private final JButton dugmeOdbij;
    private final JLabel statusna;

    public ApprovalClientUI() {
        super("Approval klijent - fakture na cekanju");

        // === tabela ===
        String[] kolone = {"ID fakture", "Klijent", "Iznos BAM", "Valuta", "Datum"};

        model = new DefaultTableModel(kolone, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;   // tabela je samo za prikaz
            }
        };

        tabela = new JTable(model);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setRowHeight(24);
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                prikaziDetalje();
            }
        });

        // === detalji ===
        detalji = new JTextArea();
        detalji.setEditable(false);
        detalji.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        detalji.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tabela),
                new JScrollPane(detalji));
        split.setDividerLocation(520);

        // === donji panel ===
        poljeRazlog = new JTextField();

        dugmeOdobri = new JButton("Odobri");
        dugmeOdbij = new JButton("Odbij");
        dugmeOdobri.addActionListener(e -> odobri());
        dugmeOdbij.addActionListener(e -> odbij());

        JPanel razlogPanel = new JPanel(new BorderLayout(6, 0));
        razlogPanel.add(new JLabel("Razlog odbijanja: "), BorderLayout.WEST);
        razlogPanel.add(poljeRazlog, BorderLayout.CENTER);

        JPanel dugmad = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        dugmad.add(dugmeOdobri);
        dugmad.add(dugmeOdbij);

        JPanel donji = new JPanel(new GridLayout(2, 1, 0, 6));
        donji.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        donji.add(razlogPanel);
        donji.add(dugmad);

        statusna = new JLabel(" Ceka se prvi zahtjev...");
        statusna.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // === slaganje prozora ===
        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        add(donji, BorderLayout.SOUTH);
        add(statusna, BorderLayout.NORTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1000, 600));
        pack();
        setLocationRelativeTo(null);

        osvjeziDugmad();
    }

    // ==================== prijem nove fakture ====================

    /**
     * Dodaje fakturu u tabelu.
     * MORA se pozvati sa Swing niti - vidi ApprovalClientMain.
     */
    public void dodajFakturu(Invoice faktura) {
        fakture.add(faktura);

        model.addRow(new Object[]{
                faktura.getId(),
                faktura.getClient() == null ? "?" : faktura.getClient().getName(),
                faktura.getTotalBAM(),
                faktura.getCurrency(),
                faktura.getDate()
        });

        statusna.setText(" Zahtjeva na cekanju: " + fakture.size());

        // ako nista nije oznaceno, oznaci prvi red
        if (tabela.getSelectedRow() < 0 && !fakture.isEmpty()) {
            tabela.setRowSelectionInterval(0, 0);
        }

        osvjeziDugmad();
        toFront();
        log.info("Novi zahtjev u tabeli: {}", faktura.getId());
    }

    // ==================== prikaz ====================

    private void prikaziDetalje() {
        int red = tabela.getSelectedRow();

        if (red < 0 || red >= fakture.size()) {
            detalji.setText("");
            osvjeziDugmad();
            return;
        }

        detalji.setText(formatiraj(fakture.get(red)));
        detalji.setCaretPosition(0);
        osvjeziDugmad();
    }

    /**
     * Zadatak trazi da se prikazu SVI podaci sa fakture, formatirano.
     */
    private String formatiraj(Invoice f) {
        StringBuilder sb = new StringBuilder();

        sb.append("FAKTURA ").append(f.getId()).append("\n");
        sb.append("=".repeat(46)).append("\n\n");

        sb.append("KLIJENT\n");
        if (f.getClient() != null) {
            sb.append("  Naziv : ").append(f.getClient().getName()).append("\n");
            sb.append("  JIB   : ").append(f.getClient().getJib()).append("\n");
            sb.append("  Email : ").append(f.getClient().getEmail()).append("\n");
        } else {
            sb.append("  (nema podataka)\n");
        }

        sb.append("\nOSNOVNI PODACI\n");
        sb.append("  Datum   : ").append(f.getDate()).append("\n");
        sb.append("  Valuta  : ").append(f.getCurrency()).append("\n");
        sb.append("  Status  : ").append(f.getStatus()).append("\n");

        sb.append("\nSTAVKE\n");
        if (f.getItems() != null) {
            int i = 1;
            for (Item stavka : f.getItems()) {
                sb.append(String.format("  %d. %-24s %6s x %10s = %12s%n",
                        i++,
                        stavka.getDescription(),
                        stavka.getQuantity(),
                        stavka.getUnitPrice(),
                        stavka.getLineTotal()));
            }
        }

        sb.append("\nIZNOS\n");
        sb.append("  Ukupno       : ").append(f.getTotal())
          .append(" ").append(f.getCurrency()).append("\n");
        sb.append("  Kurs         : ").append(f.getRate()).append("\n");
        sb.append("  Izvor kursa  : ").append(f.getRateSource()).append("\n");
        sb.append("  UKUPNO BAM   : ").append(f.getTotalBAM()).append("\n");

        return sb.toString();
    }

    private void osvjeziDugmad() {
        boolean imaIzbor = tabela.getSelectedRow() >= 0;
        dugmeOdobri.setEnabled(imaIzbor);
        dugmeOdbij.setEnabled(imaIzbor);
    }

    // ==================== odluke ====================

    private void odobri() {
        int red = tabela.getSelectedRow();
        if (red < 0) {
            return;
        }

        Invoice faktura = fakture.get(red);

        faktura.setStatus("approved");
        faktura.setApprovedBy(KORISNIK);
        faktura.setDecisionTimestamp(LocalDateTime.now().toString());

        MqClient.posalji(TOPIC_APPROVED, JsonUtil.toJson(faktura));
        log.info("Faktura {} ODOBRENA rucno", faktura.getId());

        ukloni(red);
    }

    private void odbij() {
        int red = tabela.getSelectedRow();
        if (red < 0) {
            return;
        }

        String razlog = poljeRazlog.getText().trim();

        // Zadatak: u slucaju odbijanja OBAVEZNO se unosi razlog
        if (razlog.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Za odbijanje fakture morate unijeti razlog.",
                    "Razlog je obavezan",
                    JOptionPane.WARNING_MESSAGE);
            poljeRazlog.requestFocusInWindow();
            return;
        }

        Invoice faktura = fakture.get(red);

        faktura.setStatus("rejected");
        faktura.setApprovedBy(KORISNIK);
        faktura.setRejectionReason(razlog);
        faktura.setDecisionTimestamp(LocalDateTime.now().toString());

        MqClient.posalji(TOPIC_REJECTED, JsonUtil.toJson(faktura));
        log.info("Faktura {} ODBIJENA rucno, razlog: {}", faktura.getId(), razlog);

        poljeRazlog.setText("");
        ukloni(red);
    }

    /**
     * Sklanja obradjenu fakturu iz tabele i liste.
     */
    private void ukloni(int red) {
        fakture.remove(red);
        model.removeRow(red);

        statusna.setText(" Zahtjeva na cekanju: " + fakture.size());

        if (!fakture.isEmpty()) {
            int novi = Math.min(red, fakture.size() - 1);
            tabela.setRowSelectionInterval(novi, novi);
        } else {
            detalji.setText("");
        }

        osvjeziDugmad();
    }
}