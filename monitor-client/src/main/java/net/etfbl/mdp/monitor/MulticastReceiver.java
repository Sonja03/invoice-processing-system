package net.etfbl.mdp.monitor;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prima UDP multicast notifikacije sa grupe 239.0.0.1:4446.
 *
 * Radi u svojoj niti i za svaku primljenu poruku zove zadatu funkciju.
 * Vise instanci Monitora moze se pridruziti istoj grupi - svaka dobija
 * sve poruke.
 */
public class MulticastReceiver implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MulticastReceiver.class);

    /** Zadatak: grupa 239.0.0.1, port 4446. */
    public static final String GRUPA = "239.0.0.1";
    public static final int PORT = 4446;

    /** Najveci paket koji ocekujemo. Notifikacija je oko 150 bajtova. */
    private static final int VELICINA_BAFERA = 4096;

    /** Sta se radi sa primljenom porukom - prosljedjuje ga MonitorMain. */
    private final Consumer<String> obradi;

    private volatile boolean radi = true;
    private MulticastSocket socket;

    public MulticastReceiver(Consumer<String> obradi) {
        this.obradi = obradi;
    }

    @Override
    public void run() {
        try {
            socket = new MulticastSocket(PORT);

            InetAddress adresaGrupe = InetAddress.getByName(GRUPA);
            InetSocketAddress grupa = new InetSocketAddress(adresaGrupe, PORT);
            NetworkInterface mrezniInterfejs = pronadjiInterfejs();

            socket.joinGroup(grupa, mrezniInterfejs);

            log.info("Pridruzen multicast grupi {}:{}", GRUPA, PORT);

            byte[] bafer = new byte[VELICINA_BAFERA];

            while (radi) {
                DatagramPacket paket = new DatagramPacket(bafer, bafer.length);

                socket.receive(paket);   // blokira dok paket ne stigne

                String poruka = new String(
                        paket.getData(), 0, paket.getLength(), StandardCharsets.UTF_8);

                log.info("Primljen paket od {} ({} bajtova)",
                        paket.getAddress().getHostAddress(), paket.getLength());

                obradi.accept(poruka);
            }

        } catch (Exception e) {
            if (radi) {
                log.error("Greska u multicast prijemu", e);
            }

        } finally {
            zatvori();
        }
    }

    /**
     * Na racunarima sa vise mreznih kartica treba reci kojoj se
     * prikljucujemo. Uzimamo prvu koja podrzava multicast i radi.
     */
    private NetworkInterface pronadjiInterfejs() throws Exception {
        var interfejsi = NetworkInterface.getNetworkInterfaces();

        while (interfejsi.hasMoreElements()) {
            NetworkInterface ni = interfejsi.nextElement();
            if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
                log.info("Koristim mrezni interfejs: {}", ni.getDisplayName());
                return ni;
            }
        }

        // ako nema nijedne obicne kartice, probaj loopback
        return NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
    }

    /** Zaustavlja petlju i zatvara socket. */
    public void zaustavi() {
        radi = false;
        zatvori();
    }

    private void zatvori() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("Multicast socket zatvoren");
        }
    }
}