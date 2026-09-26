package com.menswear.payments.jazzcash;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JazzCashCryptoTest {

    @Test
    void hashIsDeterministicAndVerifiable() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pp_Amount", "10000");
        fields.put("pp_BillReference", "MW-2026-1");
        fields.put("pp_Language", "EN");
        fields.put("pp_MerchantID", "MC12345");
        fields.put("pp_Password", "zaraa123");
        fields.put("pp_TxnCurrency", "PKR");
        fields.put("pp_TxnRefNo", "T202601011200001234");
        fields.put("pp_Version", "1.1");

        String salt = "sandboxsalt";
        String hash = JazzCashCrypto.secureHash(fields, salt);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertEquals(hash, JazzCashCrypto.secureHash(fields, salt));

        Map<String, String> withHash = new LinkedHashMap<>(fields);
        withHash.put("pp_SecureHash", hash);
        assertTrue(JazzCashCrypto.verify(withHash, salt));

        withHash.put("pp_SecureHash", "DEADBEEF");
        assertFalse(JazzCashCrypto.verify(withHash, salt));
    }

    @Test
    void emptyFieldsAreIgnored() {
        Map<String, String> a = Map.of("pp_Amount", "100", "pp_Empty", "");
        Map<String, String> b = Map.of("pp_Amount", "100");
        String salt = "salt";
        assertEquals(JazzCashCrypto.secureHash(a, salt), JazzCashCrypto.secureHash(b, salt));
    }
}
