package net.etfbl.mdp.aggregator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.JsonUtil;

import redis.clients.jedis.JedisPooled;

/**
 * Faza 6 - upis u Redis.
 *
 * Dvije vrste zapisa:
 *   invoice:{id}   -> kompletan JSON fakture (obican string)
 *   client:{jib}   -> hash sa poljima totalBAM, count, lastInvoiceId, lastUpdated
 *
 * Iz ovih kljuceva cita REST API u posljednjoj fazi.
 */
public class RedisStore {

    private static final Logger log = LoggerFactory.getLogger(RedisStore.class);

    private static final String HOST = "localhost";
    private static final int PORT = 6379;

    private static final JedisPooled JEDIS = new JedisPooled(HOST, PORT);

    private RedisStore() {
    }

    /**
     * Cuva kompletan JSON zapis fakture.
     * Prepisuje postojeci - faktura kroz lanac prolazi vise puta
     * i svaki put ima svjezije podatke.
     */
    public static void sacuvajFakturu(Invoice faktura) {
        String kljuc = "invoice:" + faktura.getId();
        JEDIS.set(kljuc, JsonUtil.toJson(faktura));

        log.info("Redis: {} -> status {}", kljuc, faktura.getStatus());
    }

    /**
     * Azurira statistiku klijenta. Poziva se samo za odobrene fakture.
     *
     * client:{jib} je Redis hash - jedan kljuc sa vise polja:
     *   totalBAM      zbir svih odobrenih faktura
     *   count         broj odobrenih faktura
     *   lastInvoiceId ID posljednje
     *   lastUpdated   vrijeme posljednje izmjene
     */
    public static void azurirajKlijenta(Invoice faktura) {
        if (faktura.getClient() == null || faktura.getClient().getJib() == null) {
            log.warn("Faktura {} nema JIB, preskacem statistiku klijenta", faktura.getId());
            return;
        }

        String jib = faktura.getClient().getJib();
        String kljuc = "client:" + jib;

        BigDecimal iznos = faktura.getTotalBAM() == null
                ? BigDecimal.ZERO
                : faktura.getTotalBAM();

        // procitaj zatecene vrijednosti
        String stariTotal = JEDIS.hget(kljuc, "totalBAM");
        String stariCount = JEDIS.hget(kljuc, "count");

        BigDecimal noviTotal = (stariTotal == null ? BigDecimal.ZERO : new BigDecimal(stariTotal))
                .add(iznos);

        long noviCount = (stariCount == null ? 0L : Long.parseLong(stariCount)) + 1;

        Map<String, String> polja = new HashMap<>();
        polja.put("totalBAM", noviTotal.toPlainString());
        polja.put("count", String.valueOf(noviCount));
        polja.put("lastInvoiceId", faktura.getId());
        polja.put("lastUpdated", LocalDateTime.now().toString());

        if (faktura.getClient().getName() != null) {
            polja.put("name", faktura.getClient().getName());
        }

        JEDIS.hset(kljuc, polja);

        log.info("Redis: {} -> ukupno {} BAM, {} faktura", kljuc, noviTotal, noviCount);
    }

    public static void provjeriVezu() {
        String odgovor = JEDIS.ping();
        log.info("Redis veza uspostavljena ({}:{}) - {}", HOST, PORT, odgovor);
    }
}