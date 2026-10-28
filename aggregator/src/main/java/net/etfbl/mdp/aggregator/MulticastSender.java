package net.etfbl.mdp.aggregator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Faza 6 - slanje UDP multicast notifikacija.
 *
 * Salje na grupu 239.0.0.1:4446. Bilo koji broj Monitor klijenata
 * moze se pridruziti grupi i primati iste poruke.
 *
 * UDP ne garantuje isporuku - nema potvrde, nema ponovnog slanja.
 * Za notifikacije je to prihvatljivo.
 */
public class MulticastSender {

    private static final Logger log = LoggerFactory.getLogger(MulticastSender.class);

    /** Zadatak: grupa 239.0.0.1, port 4446. */
    public static final String GRUPA = "239.0.0.1";
    public static final int PORT = 4446;

    /**
     * TTL odredjuje kroz koliko rutera paket smije proci.
     * 1 znaci "samo lokalna mreza", sto je ovdje dovoljno.
     */
    private static final int TTL = 1;

    private static MulticastSocket socket;
    private static InetAddress adresaGrupe;

    private MulticastSender() {
    }

    /** Poziva se jednom, pri pokretanju servisa. */
    public static void pokreni() throws IOException {
        adresaGrupe = InetAddress.getByName(GRUPA);

        socket = new MulticastSocket();
        socket.setTimeToLive(TTL);

        log.info("Multicast posiljalac spreman za grupu {}:{}", GRUPA, PORT);
    }

    /**
     * Salje JSON poruku svima koji su pridruzeni grupi.
     *
     * Ne baca izuzetak dalje - neuspjela notifikacija ne smije
     * prekinuti obradu fakture.
     */
    public static void posalji(String json) {
        try {
            byte[] podaci = json.getBytes(StandardCharsets.UTF_8);

            DatagramPacket paket = new DatagramPacket(
                    podaci, podaci.length, adresaGrupe, PORT);

            socket.send(paket);

            log.info("Multicast poslat na {}:{} ({} bajtova)", GRUPA, PORT, podaci.length);

        } catch (Exception e) {
            log.error("Greska pri slanju multicast notifikacije", e);
        }
    }

    public static void zaustavi() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("Multicast socket zatvoren");
        }
    }
}