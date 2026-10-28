package net.etfbl.mdp.restapi;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import net.etfbl.mdp.common.model.Invoice;

/**
 * Obradjuje endpoint:
 *
 *   GET /api/stats
 *
 * Zadatak trazi: ukupno faktura, ukupno BAM, prosjecan iznos, broj pending.
 * Statistika se racuna u letu iz svih zapisa u Redisu.
 */
public class StatsHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(StatsHandler.class);

    private static final int SKALA_NOVCA = 2;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        log.info("{} {}", exchange.getRequestMethod(), exchange.getRequestURI());

        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtil.posaljiGresku(exchange, 405, "Dozvoljen je samo GET");
                return;
            }

            HttpUtil.posaljiJson(exchange, izracunaj());

        } catch (Exception e) {
            log.error("Greska pri obradi {}", exchange.getRequestURI(), e);
            HttpUtil.posaljiGresku(exchange, 500, "Greska na serveru: " + e.getMessage());
        }
    }

    private Map<String, Object> izracunaj() {
        List<Invoice> fakture = RedisReader.sveFakture();

        BigDecimal ukupnoBAM = BigDecimal.ZERO;
        int brojPending = 0;

        // koliko ih je po svakom statusu - TreeMap da budu abecedno
        Map<String, Integer> poStatusu = new TreeMap<>();

        for (Invoice f : fakture) {
            if (f.getTotalBAM() != null) {
                ukupnoBAM = ukupnoBAM.add(f.getTotalBAM());
            }

            String status = f.getStatus() == null ? "unknown" : f.getStatus();

            poStatusu.merge(status, 1, Integer::sum);

            if ("pending".equalsIgnoreCase(status)) {
                brojPending++;
            }
        }

        int ukupnoFaktura = fakture.size();

        BigDecimal prosjek = ukupnoFaktura == 0
                ? BigDecimal.ZERO
                : ukupnoBAM.divide(BigDecimal.valueOf(ukupnoFaktura),
                                   SKALA_NOVCA, RoundingMode.HALF_UP);

        Map<String, Object> odgovor = new LinkedHashMap<>();
        odgovor.put("ukupnoFaktura", ukupnoFaktura);
        odgovor.put("ukupnoBAM", ukupnoBAM.setScale(SKALA_NOVCA, RoundingMode.HALF_UP)
                                          .toPlainString());
        odgovor.put("prosjecanIznosBAM", prosjek.toPlainString());
        odgovor.put("brojPending", brojPending);
        odgovor.put("poStatusu", poStatusu);

        log.info("Statistika: {} faktura, {} BAM ukupno, {} pending",
                ukupnoFaktura, ukupnoBAM, brojPending);

        return odgovor;
    }
}