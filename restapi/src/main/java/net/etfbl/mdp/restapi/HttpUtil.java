package net.etfbl.mdp.restapi;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import net.etfbl.mdp.common.util.JsonUtil;

/**
 * Pomocne metode za slanje HTTP odgovora.
 *
 * Zadatak trazi da svi odgovori budu JSON, pa se to radi na
 * jednom mjestu umjesto u svakom handleru.
 */
public class HttpUtil {

    private HttpUtil() {
    }

    /** Uspjesan odgovor - 200 OK sa JSON tijelom. */
    public static void posaljiJson(HttpExchange exchange, Object objekat) throws IOException {
        posalji(exchange, 200, JsonUtil.toPrettyJson(objekat));
    }

    /** Greska - zadati status kod sa JSON porukom. */
    public static void posaljiGresku(HttpExchange exchange, int status, String poruka)
            throws IOException {

        Map<String, Object> tijelo = new HashMap<>();
        tijelo.put("error", poruka);
        tijelo.put("status", status);

        posalji(exchange, status, JsonUtil.toJson(tijelo));
    }

    /**
     * Salje odgovor i zatvara razmjenu.
     *
     * Redoslijed je propisan: prvo zaglavlja, pa sendResponseHeaders,
     * pa tijelo. Obrnuto ne radi.
     */
    private static void posalji(HttpExchange exchange, int status, String tijelo)
            throws IOException {

        byte[] bajtovi = tijelo.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        exchange.sendResponseHeaders(status, bajtovi.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bajtovi);
        }
    }

    /**
     * Izvlaci query parametre iz URL-a.
     *
     * Iz "/api/invoices?status=approved&x=1" pravi {status=approved, x=1}.
     * Vraca praznu mapu ako parametara nema.
     */
    public static Map<String, String> parametri(HttpExchange exchange) {
        Map<String, String> mapa = new HashMap<>();

        URI uri = exchange.getRequestURI();
        String query = uri.getRawQuery();

        if (query == null || query.isBlank()) {
            return mapa;
        }

        for (String par : query.split("&")) {
            int znak = par.indexOf('=');
            if (znak > 0) {
                String kljuc = par.substring(0, znak);
                String vrijednost = par.substring(znak + 1);
                mapa.put(kljuc, vrijednost);
            }
        }

        return mapa;
    }

    /**
     * Vraca dio putanje iza zadatog prefiksa.
     *
     * Iz "/api/invoices/INV-2026-003" sa prefiksom "/api/invoices/"
     * vraca "INV-2026-003". Prazan string ako nema nicega iza.
     */
    public static String dioPutanje(HttpExchange exchange, String prefiks) {
        String putanja = exchange.getRequestURI().getPath();

        if (putanja.length() <= prefiks.length()) {
            return "";
        }

        return putanja.substring(prefiks.length());
    }
}