package net.etfbl.mdp.restapi;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Obradjuje endpoint:
 *
 *   GET /api/clients/{jib}/summary
 *
 * Cita Redis hash client:{jib} koji je upisao Aggregator -
 * polja totalBAM, count, lastInvoiceId, lastUpdated.
 */
public class ClientHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private static final String PREFIKS = "/api/clients/";
    private static final String SUFIKS = "/summary";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        log.info("{} {}", exchange.getRequestMethod(), exchange.getRequestURI());

        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtil.posaljiGresku(exchange, 405, "Dozvoljen je samo GET");
                return;
            }

            // iz "/api/clients/4400644000005/summary" ostaje "4400644000005/summary"
            String ostatak = HttpUtil.dioPutanje(exchange, PREFIKS);

            if (!ostatak.endsWith(SUFIKS)) {
                HttpUtil.posaljiGresku(exchange, 400,
                        "Ocekivana putanja: /api/clients/{jib}/summary");
                return;
            }

            String jib = ostatak.substring(0, ostatak.length() - SUFIKS.length());

            if (jib.isBlank()) {
                HttpUtil.posaljiGresku(exchange, 400, "JIB nije zadat");
                return;
            }

            posaljiSummary(exchange, jib);

        } catch (Exception e) {
            log.error("Greska pri obradi {}", exchange.getRequestURI(), e);
            HttpUtil.posaljiGresku(exchange, 500, "Greska na serveru: " + e.getMessage());
        }
    }

    private void posaljiSummary(HttpExchange exchange, String jib) throws IOException {
        Map<String, String> hash = RedisReader.klijent(jib);

        if (hash.isEmpty()) {
            log.warn("Klijent {} nema nijednu odobrenu fakturu", jib);
            HttpUtil.posaljiGresku(exchange, 404,
                    "Klijent " + jib + " nije pronadjen");
            return;
        }

        // LinkedHashMap cuva redoslijed, pa je JSON uredan
        Map<String, Object> odgovor = new LinkedHashMap<>();

        odgovor.put("jib", jib);
        odgovor.put("name", hash.getOrDefault("name", "?"));
        odgovor.put("totalBAM", hash.getOrDefault("totalBAM", "0"));
        odgovor.put("count", Long.parseLong(hash.getOrDefault("count", "0")));
        odgovor.put("lastInvoiceId", hash.getOrDefault("lastInvoiceId", "?"));
        odgovor.put("lastUpdated", hash.getOrDefault("lastUpdated", "?"));

        log.info("Klijent {}: {} BAM, {} faktura",
                jib, odgovor.get("totalBAM"), odgovor.get("count"));

        HttpUtil.posaljiJson(exchange, odgovor);
    }
}