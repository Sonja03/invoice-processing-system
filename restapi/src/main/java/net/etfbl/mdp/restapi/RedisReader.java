package net.etfbl.mdp.restapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.JsonUtil;

import redis.clients.jedis.JedisPooled;

/**
 * Citanje podataka iz Redisa.
 *
 * REST API ne slusa nijedan topic - sve sto prikazuje upisao je
 * Aggregator pod kljuceve invoice:{id} i client:{jib}.
 */
public class RedisReader {

    private static final Logger log = LoggerFactory.getLogger(RedisReader.class);

    private static final String HOST = "localhost";
    private static final int PORT = 6379;

    private static final String PREFIKS_FAKTURE = "invoice:";
    private static final String PREFIKS_KLIJENTI = "client:";

    private static final JedisPooled JEDIS = new JedisPooled(HOST, PORT);

    private RedisReader() {
    }

    /**
     * Vraca sve fakture iz Redisa.
     *
     * Koristi SCAN, a ne KEYS - KEYS blokira Redis dok prolazi kroz
     * sve kljuceve, sto je na vecoj bazi neprihvatljivo.
     */
    public static List<Invoice> sveFakture() {
        List<Invoice> fakture = new ArrayList<>();

        for (String kljuc : skeniraj(PREFIKS_FAKTURE + "*")) {
            Invoice faktura = ucitajPoKljucu(kljuc);
            if (faktura != null) {
                fakture.add(faktura);
            }
        }

        log.debug("Procitano {} faktura iz Redisa", fakture.size());
        return fakture;
    }

    /** Vraca jednu fakturu, ili null ako je nema. */
    public static Invoice faktura(String id) {
        return ucitajPoKljucu(PREFIKS_FAKTURE + id);
    }

    /**
     * Vraca fakture sa zadatim statusom.
     * Statusi: approved, rejected, failed, pending, enriched.
     */
    public static List<Invoice> faktureSaStatusom(String status) {
        List<Invoice> rezultat = new ArrayList<>();

        for (Invoice f : sveFakture()) {
            if (status.equalsIgnoreCase(f.getStatus())) {
                rezultat.add(f);
            }
        }

        return rezultat;
    }

    /**
     * Vraca hash klijenta kao mapu, ili praznu mapu ako klijenta nema.
     * Polja: totalBAM, count, lastInvoiceId, lastUpdated, name.
     */
    public static Map<String, String> klijent(String jib) {
        return JEDIS.hgetAll(PREFIKS_KLIJENTI + jib);
    }

    public static void provjeriVezu() {
        String odgovor = JEDIS.ping();
        log.info("Redis veza uspostavljena ({}:{}) - {}", HOST, PORT, odgovor);
    }

    // ---------- pomocne metode ----------

    private static Invoice ucitajPoKljucu(String kljuc) {
        try {
            String json = JEDIS.get(kljuc);

            if (json == null) {
                return null;
            }

            return JsonUtil.fromJson(json, Invoice.class);

        } catch (Exception e) {
            log.error("Neispravan zapis pod kljucem {}", kljuc, e);
            return null;
        }
    }

    /**
     * SCAN prolazi kroz kljuceve u komadima, bez blokiranja servera.
     * Vraca kursor koji se prosljedjuje sljedecem pozivu dok ne bude "0".
     */
    private static Set<String> skeniraj(String uzorak) {
        return JEDIS.keys(uzorak);
    }
}