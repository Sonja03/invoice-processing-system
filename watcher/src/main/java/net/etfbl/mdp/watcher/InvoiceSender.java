package net.etfbl.mdp.watcher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.util.HashUtil;

/**
 * Uzima fajlove iz reda, salje ih Parseru i ceka ACK.
 * Tek nakon ACK-a premjesta fajl u processed.
 */
public class InvoiceSender implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSender.class);

    private static final String PARSER_HOST = "localhost";
    private static final int PARSER_PORT = 9000;

    /** Koliko cekamo ACK prije nego odustanemo. */
    private static final int TIMEOUT_MS = 10000;

    private final BlockingQueue<Path> red;
    private final Path processedDir;

    public InvoiceSender(BlockingQueue<Path> red, Path processedDir) {
        this.red = red;
        this.processedDir = processedDir;
    }

    @Override
    public void run() {
        while (true) {
            Path fajl = null;
            try {
                fajl = red.take();   // blokira dok red ne dobije fajl
                obradi(fajl);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("Greska pri slanju fajla {}", fajl, e);
            }
        }
    }

    private void obradi(Path fajl) throws Exception {

        // Fajl je mozda jos u toku pisanja - ENTRY_CREATE se javi cim fajl
        // nastane, ne kad se dopise. Cekamo da mu se velicina smiri.
        if (!cekajDaSeStabilizuje(fajl)) {
            log.warn("Fajl {} nije stabilan ili je nestao, preskacem", fajl.getFileName());
            return;
        }

        byte[] sadrzaj = Files.readAllBytes(fajl);
        byte[] hash = HashUtil.sha256(sadrzaj);
        String ime = fajl.getFileName().toString();

        log.info("Saljem {} ({} bajtova, hash {})", ime, sadrzaj.length,
                HashUtil.toHex(hash).substring(0, 16) + "...");

        String odgovor = posalji(ime, sadrzaj, hash);

        if (odgovor != null && odgovor.startsWith("ACK:")) {
            log.info("ACK primljen za {}", ime);
            premjesti(fajl);
        } else {
            log.error("Parser nije potvrdio {} (odgovor: {}), fajl ostaje u inboxu",
                    ime, odgovor);
        }
    }

    /**
     * Salje poruku po protokolu i vraca ACK odgovor.
     *
     *   ime     (writeUTF)
     *   header  (4 bajta)  - duzina XML sadrzaja
     *   payload (N bajta)
     *   footer  (32 bajta) - SHA-256
     */
    private String posalji(String ime, byte[] sadrzaj, byte[] hash) {
        try (Socket socket = new Socket(PARSER_HOST, PARSER_PORT)) {

            socket.setSoTimeout(TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            out.writeUTF(ime);
            out.writeInt(sadrzaj.length);
            out.write(sadrzaj);
            out.write(hash);
            out.flush();

            return in.readUTF();   // ceka ACK

        } catch (IOException e) {
            log.error("Neuspjela komunikacija sa Parserom za {}: {}", ime, e.getMessage());
            return null;
        }
    }

    /**
     * ENTRY_CREATE se javi cim fajl nastane, a ne kad je dopisan do kraja.
     * Cekamo da se velicina ne mijenja dva puta zaredom.
     */
    private boolean cekajDaSeStabilizuje(Path fajl) throws InterruptedException {
        long prethodna = -1;

        for (int pokusaj = 0; pokusaj < 25; pokusaj++) {
            if (!Files.exists(fajl)) {
                return false;
            }

            long trenutna;
            try {
                trenutna = Files.size(fajl);
            } catch (IOException e) {
                Thread.sleep(100);
                continue;
            }

            if (trenutna > 0 && trenutna == prethodna) {
                return true;
            }

            prethodna = trenutna;
            Thread.sleep(100);
        }

        return false;
    }

    private void premjesti(Path fajl) {
        try {
            Path cilj = processedDir.resolve(fajl.getFileName());
            Files.move(fajl, cilj, StandardCopyOption.REPLACE_EXISTING);
            log.info("Premjesten u processed: {}", fajl.getFileName());
        } catch (IOException e) {
            log.error("Nije uspjelo premjestanje fajla {}", fajl.getFileName(), e);
        }
    }
}