package net.etfbl.mdp.watcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Faza 1 - Watcher servis.
 *
 * Prati folder inbox. Kada se pojavi novi XML fajl, stavlja ga u red,
 * a radne niti ga citaju, salju Parseru i cekaju ACK.
 *
 * Red je ogranicene velicine - kad se napuni, nit koja detektuje fajlove
 * blokira. To je backpressure iz zadatka: ne gubimo fajlove i ne
 * preopterecujemo Parser.
 */
public class WatcherMain {

    private static final Logger log = LoggerFactory.getLogger(WatcherMain.class);

    // === PODESI OVE PUTANJE PREMA SVOM RACUNARU ===
    private static final Path INBOX =
            Path.of("C:/Users/WINDOWS11/eclipse-workspace/mdpProjekat/invoices/inbox");
    private static final Path PROCESSED =
            Path.of("C:/Users/WINDOWS11/eclipse-workspace/mdpProjekat/invoices/processed");

    /** Koliko fajlova moze cekati u redu prije nego detekcija blokira. */
    private static final int VELICINA_REDA = 200;

    /** Koliko fajlova se salje istovremeno. */
    private static final int BROJ_NITI = 4;

    /** Na koliko sekundi se inbox ponovo skenira. */
    private static final int INTERVAL_SKENIRANJA = 30;

    public static void main(String[] args) {
        try {
            Files.createDirectories(INBOX);
            Files.createDirectories(PROCESSED);

            BlockingQueue<Path> red = new ArrayBlockingQueue<>(VELICINA_REDA);

            // radne niti koje salju fajlove
            for (int i = 1; i <= BROJ_NITI; i++) {
                Thread nit = new Thread(new InvoiceSender(red, PROCESSED), "sender-" + i);
                nit.setDaemon(true);
                nit.start();
            }
            log.info("Pokrenuto {} radnih niti", BROJ_NITI);

            // fajlovi koji su vec bili u folderu prije pokretanja
            skenirajInbox(red);

            pokreniPeriodicnoSkeniranje(red);

            // pracenje novih
            pratiFolder(red);

        } catch (Exception e) {
            log.error("Greska u Watcher servisu", e);
        }
    }

    /**
     * Sigurnosna mreza. WatchService javlja samo NOVE dogadjaje, pa fajl
     * koji je jednom preskocen (nestabilan, Parser nedostupan, red pun)
     * nikad se ne bi ponovo javio i ostao bi zauvijek u inboxu.
     */
    private static void pokreniPeriodicnoSkeniranje(BlockingQueue<Path> red) {
        ScheduledExecutorService skener = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "skener");
            t.setDaemon(true);
            return t;
        });

        skener.scheduleWithFixedDelay(() -> {
            try {
                skenirajInbox(red);
            } catch (Exception e) {
                log.error("Greska pri periodicnom skeniranju", e);
            }
        }, INTERVAL_SKENIRANJA, INTERVAL_SKENIRANJA, TimeUnit.SECONDS);

        log.info("Periodicno skeniranje inboxa svakih {} sekundi", INTERVAL_SKENIRANJA);
    }

    /**
     * Ubacuje u red sve XML fajlove koji su trenutno u inboxu.
     *
     * Koristi offer(), a ne put() - ako je red pun, fajl jednostavno ostaje
     * u inboxu i bice pokupljen pri sljedecem skeniranju. Skener ne smije
     * da blokira.
     */
    private static void skenirajInbox(BlockingQueue<Path> red) throws IOException {
        try (Stream<Path> fajlovi = Files.list(INBOX)) {
            for (Path fajl : fajlovi.filter(WatcherMain::jeXml).toList()) {
                if (red.offer(fajl)) {
                    log.info("U red (skeniranje): {}", fajl.getFileName());
                }
            }
        }
    }

    /**
     * WatchService javlja kada se u folderu nesto promijeni.
     */
    private static void pratiFolder(BlockingQueue<Path> red) throws IOException, InterruptedException {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {

            INBOX.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            log.info("Pratim folder {}", INBOX);

            while (true) {
                WatchKey key = watcher.take();   // blokira dok se nesto ne desi

                for (WatchEvent<?> event : key.pollEvents()) {

                    // OVERFLOW znaci da je WatchService izgubio dogadjaje jer
                    // ih je bilo previse odjednom. Tada rucno skeniramo folder.
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        log.warn("OVERFLOW - skeniram folder rucno");
                        try {
                            skenirajInbox(red);
                        } catch (Exception e) {
                            log.error("Greska pri skeniranju", e);
                        }
                        continue;
                    }

                    Path ime = (Path) event.context();
                    Path fajl = INBOX.resolve(ime);

                    if (!jeXml(fajl)) {
                        continue;
                    }

                    // put() blokira ako je red pun - to je backpressure
                    red.put(fajl);
                    log.info("U red: {} (u redu ih je {})", ime, red.size());
                }

                if (!key.reset()) {
                    log.error("Folder vise nije dostupan, prekidam");
                    break;
                }
            }
        }
    }

    private static boolean jeXml(Path fajl) {
        return fajl.getFileName().toString().toLowerCase().endsWith(".xml");
    }
}