package net.etfbl.mdp.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jedan zajednicki ObjectMapper za sve servise.
 *
 * Bitno je da svi koriste iste postavke: ako Parser serijalizuje datum
 * kao "2025-05-01" a Enrichment ocekuje niz brojeva, poruka pukne na
 * drugom kraju reda i tesko se nalazi zasto.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // bez ovog modula Jackson ne zna sta ce sa LocalDate
            .registerModule(new JavaTimeModule())
            // datum kao "2025-05-01", a ne kao [2025,5,1]
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // ako neko doda novo polje u JSON, stari servis ga ignorise umjesto da pukne
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Objekat -> JSON string. Koristi se pri slanju na MQ i upisu u Redis. */
    public static String toJson(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Greska pri serijalizaciji u JSON", e);
        }
    }

    /** Isto, samo formatirano - zgodno za logovanje i za GUI prikaz fakture. */
    public static String toPrettyJson(Object object) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Greska pri serijalizaciji u JSON", e);
        }
    }

    /** JSON string -> objekat. Koristi se pri citanju sa MQ-a i iz Redisa. */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Greska pri citanju JSON-a", e);
        }
    }
}