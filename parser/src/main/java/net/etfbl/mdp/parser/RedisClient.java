package net.etfbl.mdp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

/**
 * Provjera duplikata faktura u Redisu (Faza 2).
 *
 * Kljuc je dedup:invoice:{id}. Ako ga uspijemo postaviti, faktura je nova.
 * Ako vec postoji, rijec je o duplikatu.
 */
public class RedisClient {

    private static final Logger log = LoggerFactory.getLogger(RedisClient.class);

    private static final String HOST = "localhost";
    private static final int PORT = 6379;

    /** Kljuc istice nakon 24 sata, da Redis ne raste bez kraja. */
    private static final long TTL_SEKUNDI = 86400;

    /** Jedan pool za cijeli servis - dijele ga sve radne niti. */
    private static final JedisPooled JEDIS = new JedisPooled(HOST, PORT);

    private RedisClient() {
    }

    /**
     * Pokusava rezervisati ID fakture.
     *
     * NX znaci "postavi samo ako kljuc NE postoji" i to je jedna atomicna
     * operacija. Da smo radili exists() pa set(), dvije niti bi mogle
     * istovremeno proci provjeru i obje bi obradile istu fakturu.
     *
     * @return true ako je faktura nova, false ako je duplikat
     */
    public static boolean rezervisiId(String invoiceId) {
        String kljuc = "dedup:invoice:" + invoiceId;

        String rezultat = JEDIS.set(kljuc, "1", new SetParams().nx().ex(TTL_SEKUNDI));

        boolean nova = "OK".equals(rezultat);

        if (nova) {
            log.debug("ID {} rezervisan", invoiceId);
        } else {
            log.warn("DUPLIKAT: faktura {} je vec obradjena", invoiceId);
        }

        return nova;
    }

    /**
     * Oslobadja rezervaciju ako obrada nije uspjela.
     *
     * Bez ovoga bi faktura kod koje je pukla validacija ostala zauvijek
     * oznacena kao "vec obradjena", pa se nikad ne bi mogla ponoviti.
     */
    public static void oslobodiId(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return;
        }
        JEDIS.del("dedup:invoice:" + invoiceId);
        log.info("Rezervacija ID-a {} oslobodjena zbog neuspjele obrade", invoiceId);
    }

    /** Za testiranje - brise sve dedup kljuceve. */
    public static void obrisiSveDedupKljuceve() {
        JEDIS.keys("dedup:invoice:*").forEach(JEDIS::del);
        log.info("Obrisani svi dedup kljucevi");
    }

    public static void provjeriVezu() {
        String odgovor = JEDIS.ping();
        log.info("Redis veza uspostavljena ({}:{}) - {}", HOST, PORT, odgovor);
    }
}