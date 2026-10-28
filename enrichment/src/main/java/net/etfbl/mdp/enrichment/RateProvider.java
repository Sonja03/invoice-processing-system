package net.etfbl.mdp.enrichment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import net.etfbl.mdp.common.util.JsonUtil;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

/**
 * Faza 4 - dohvatanje kursa valute prema BAM.
 *
 * Prvo se gleda Redis cache. Tek ako ga nema, zove se REST API
 * i rezultat se kesira na sat vremena.
 */
public class RateProvider {

    private static final Logger log = LoggerFactory.getLogger(RateProvider.class);

    private static final String API_URL = "https://open.er-api.com/v6/latest/BAM";

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    /** Zadatak: kesirana vrijednost vazi sat vremena. */
    private static final long CACHE_TTL_SEKUNDI = 3600;

    /** Na koliko decimala se zaokruzuje kurs. */
    private static final int SKALA = 6;

    private static final JedisPooled JEDIS = new JedisPooled(REDIS_HOST, REDIS_PORT);

    /** HttpClient je siguran za vise niti - jedan za cijeli servis. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Kurs i podatak odakle je dosao.
     * rateSource ide u JSON fakture, kako zadatak trazi.
     */
    public record Kurs(BigDecimal vrijednost, String izvor) {
    }

    private RateProvider() {
    }

    /**
     * Vraca koliko BAM vrijedi jedna jedinica date valute,
     * ili null ako kurs nije dostupan ni iz cachea ni sa API-ja.
     */
    public static Kurs dohvatiKurs(String valuta) {

        if (valuta == null || valuta.isBlank()) {
            log.error("Valuta nije zadata");
            return null;
        }

        String v = valuta.toUpperCase();

        // BAM prema BAM je uvijek 1 - nema potrebe zvati nikoga
        if ("BAM".equals(v)) {
            return new Kurs(BigDecimal.ONE, "LOCAL");
        }

        String kljuc = "rate:" + v + ":" + LocalDate.now();

        // === 1. cache ===
        try {
            String kesirano = JEDIS.get(kljuc);
            if (kesirano != null) {
                log.info("Kurs {} iz cachea: {}", v, kesirano);
                return new Kurs(new BigDecimal(kesirano), "CACHE");
            }
        } catch (Exception e) {
            log.warn("Redis nije dostupan, idem direktno na API: {}", e.getMessage());
        }

        // === 2. REST API ===
        BigDecimal kurs = pozoviApi(v);

        if (kurs == null) {
            log.error("Kurs za {} nije dostupan", v);
            return null;
        }

        // === 3. upis u cache ===
        try {
            JEDIS.set(kljuc, kurs.toPlainString(), new SetParams().ex(CACHE_TTL_SEKUNDI));
            log.info("Kurs {} kesiran na {} sekundi", v, CACHE_TTL_SEKUNDI);
        } catch (Exception e) {
            log.warn("Nije uspjelo kesiranje kursa {}: {}", v, e.getMessage());
        }

        return new Kurs(kurs, "API");
    }

    /**
     * Poziva REST API i vraca koliko BAM vrijedi jedna jedinica valute.
     *
     * API vraca kurseve SA BAZOM BAM, tj. "EUR": 0.511292 znaci
     * 1 BAM = 0.511292 EUR. Nama treba obrnuto, pa se racuna reciprocna
     * vrijednost: 1 EUR = 1 / 0.511292 = 1.955826 BAM.
     */
    private static BigDecimal pozoviApi(String valuta) {
        try {
            HttpRequest zahtjev = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            log.info("Pozivam {} za valutu {}", API_URL, valuta);

            HttpResponse<String> odgovor =
                    HTTP.send(zahtjev, HttpResponse.BodyHandlers.ofString());

            if (odgovor.statusCode() != 200) {
                log.error("API vratio status {}", odgovor.statusCode());
                return null;
            }

            JsonNode root = JsonUtil.mapper().readTree(odgovor.body());

            if (!"success".equals(root.path("result").asText())) {
                log.error("API nije vratio success: {}", root.path("result").asText());
                return null;
            }

            JsonNode cvor = root.path("rates").path(valuta);

            if (cvor.isMissingNode() || cvor.isNull()) {
                log.error("API ne poznaje valutu {}", valuta);
                return null;
            }

            BigDecimal valutaPoBam = cvor.decimalValue();

            if (valutaPoBam.signum() <= 0) {
                log.error("API vratio nekorektan kurs za {}: {}", valuta, valutaPoBam);
                return null;
            }

            BigDecimal bamPoValuti =
                    BigDecimal.ONE.divide(valutaPoBam, SKALA, RoundingMode.HALF_UP);

            log.info("1 {} = {} BAM", valuta, bamPoValuti);
            return bamPoValuti;

        } catch (Exception e) {
            log.error("Greska pri pozivu kursnog API-ja: {}", e.getMessage());
            return null;
        }
    }
}