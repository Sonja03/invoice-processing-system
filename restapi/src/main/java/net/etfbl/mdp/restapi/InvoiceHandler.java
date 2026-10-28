package net.etfbl.mdp.restapi;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import net.etfbl.mdp.common.model.Invoice;

/**
 * Obradjuje tri endpointa:
 *
 *   GET /api/invoices                    lista svih faktura
 *   GET /api/invoices?status={status}    filtriranje po statusu
 *   GET /api/invoices/{id}               jedna faktura
 *
 * Ugradjeni HttpServer nema rutiranje sa parametrima, pa se
 * dio putanje iza prefiksa vadi rucno.
 */
public class InvoiceHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceHandler.class);

    private static final String PREFIKS = "/api/invoices";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String metoda = exchange.getRequestMethod();
        String putanja = exchange.getRequestURI().getPath();

        log.info("{} {}", metoda, exchange.getRequestURI());

        try {
            if (!"GET".equalsIgnoreCase(metoda)) {
                HttpUtil.posaljiGresku(exchange, 405, "Dozvoljen je samo GET");
                return;
            }

            // /api/invoices/{id}  -->  ima nesto iza prefiksa
            String id = HttpUtil.dioPutanje(exchange, PREFIKS + "/");

            if (!putanja.equals(PREFIKS) && !id.isBlank()) {
                jednaFaktura(exchange, id);
                return;
            }

            // /api/invoices  ili  /api/invoices?status=...
            listaFaktura(exchange);

        } catch (Exception e) {
            log.error("Greska pri obradi {}", exchange.getRequestURI(), e);
            HttpUtil.posaljiGresku(exchange, 500, "Greska na serveru: " + e.getMessage());
        }
    }

    /**
     * GET /api/invoices/{id}
     */
    private void jednaFaktura(HttpExchange exchange, String id) throws IOException {
        Invoice faktura = RedisReader.faktura(id);

        if (faktura == null) {
            log.warn("Faktura {} nije pronadjena", id);
            HttpUtil.posaljiGresku(exchange, 404, "Faktura " + id + " nije pronadjena");
            return;
        }

        HttpUtil.posaljiJson(exchange, faktura);
    }

    /**
     * GET /api/invoices
     * GET /api/invoices?status={status}
     */
    private void listaFaktura(HttpExchange exchange) throws IOException {
        Map<String, String> parametri = HttpUtil.parametri(exchange);
        String status = parametri.get("status");

        List<Invoice> fakture = (status == null || status.isBlank())
                ? RedisReader.sveFakture()
                : RedisReader.faktureSaStatusom(status);

        // najnovije prvo - sortiranje po ID-u opadajuce
        fakture.sort((a, b) -> {
            String ia = a.getId() == null ? "" : a.getId();
            String ib = b.getId() == null ? "" : b.getId();
            return ib.compareTo(ia);
        });

        log.info("Vracam {} faktura{}", fakture.size(),
                status == null ? "" : " sa statusom " + status);

        HttpUtil.posaljiJson(exchange, fakture);
    }
}