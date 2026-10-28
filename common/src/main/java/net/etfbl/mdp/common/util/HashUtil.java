package net.etfbl.mdp.common.util;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * SHA-256 nad sadrzajem fakture.
 *
 * Watcher racuna hash i salje ga kao 32-bajtni footer,
 * Parser racuna isti hash nad primljenim bajtovima i poredi.
 */
public final class HashUtil {

    /** SHA-256 uvijek daje tacno 32 bajta - odatle velicina footera. */
    public static final int SHA256_LENGTH = 32;

    private HashUtil() {
        // sluzbena klasa, ne pravi se instanca
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 nije dostupan", e);
        }
    }

    /**
     * Poredjenje hasheva.
     */
    public static boolean matches(byte[] expected, byte[] actual) {
        return MessageDigest.isEqual(expected, actual);
    }

    /** Za logovanje - bajtovi u citljiv hex string. */
    public static String toHex(byte[] data) {
        return HexFormat.of().formatHex(data);
    }
}