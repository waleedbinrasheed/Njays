package com.menswear.payments.jazzcash;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * JazzCash merchant-page integrity hash (HMAC-SHA256).
 * Sort non-empty fields alphabetically, prepend integrity salt with {@code &}-joined values,
 * HMAC with the salt as key, uppercase hex.
 */
public final class JazzCashCrypto {

    private JazzCashCrypto() {}

    public static String secureHash(Map<String, String> fields, String integritySalt) {
        if (integritySalt == null || integritySalt.isBlank()) {
            throw new IllegalStateException("JazzCash integrity salt is not configured");
        }
        TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            if ("pp_SecureHash".equalsIgnoreCase(e.getKey())) {
                continue;
            }
            if (e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            sorted.put(e.getKey(), e.getValue());
        }

        StringBuilder sb = new StringBuilder(integritySalt);
        for (String value : sorted.values()) {
            sb.append('&').append(value);
        }
        return hmacSha256Hex(sb.toString(), integritySalt).toUpperCase(Locale.ROOT);
    }

    public static boolean verify(Map<String, String> payload, String integritySalt) {
        String received = firstNonBlank(payload, "pp_SecureHash", "pp_secureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        String expected = secureHash(payload, integritySalt);
        return constantTimeEquals(expected, received.trim().toUpperCase(Locale.ROOT));
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute JazzCash HMAC", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String firstNonBlank(Map<String, String> map, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)
                        && e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}
