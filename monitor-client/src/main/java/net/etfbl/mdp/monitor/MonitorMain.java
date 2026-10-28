package net.etfbl.mdp.monitor;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitor klijent - pokretanje.
 *
 * Moze se pokrenuti kao N nezavisnih instanci. Svaka se pridruzuje
 * istoj multicast grupi i prima sve notifikacije.
 *
 * Naslov prozora moze se zadati argumentom: java MonitorMain "Monitor 2"
 */
public class MonitorMain {

    private static final Logger log = LoggerFactory.getLogger(MonitorMain.class);

    public static void main(String[] args) {

        String naslov = args.length > 0
                ? "Monitor - " + args[0]
                : "Monitor - PID " + ProcessHandle.current().pid();

        SwingUtilities.invokeLater(() -> {
            MonitorUI ui = new MonitorUI(naslov);
            ui.setVisible(true);

            // Prijemnik radi u svojoj niti. Za svaku poruku prebacuje
            // posao na EDT, jer tabelu smije mijenjati samo Swing nit.
            MulticastReceiver prijemnik = new MulticastReceiver(
                    poruka -> SwingUtilities.invokeLater(() -> ui.dodajNotifikaciju(poruka)));

            Thread nit = new Thread(prijemnik, "multicast-prijemnik");
            nit.setDaemon(true);
            nit.start();

            log.info("{} pokrenut, slusam {}:{}",
                    naslov, MulticastReceiver.GRUPA, MulticastReceiver.PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Gasim Monitor...");
                prijemnik.zaustavi();
            }, "shutdown-hook"));
        });
    }
}