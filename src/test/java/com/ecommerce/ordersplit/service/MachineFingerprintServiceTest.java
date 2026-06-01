package com.ecommerce.ordersplit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MachineFingerprintServiceTest {

    private final MachineFingerprintService machineFingerprintService = new MachineFingerprintService();

    @Test
    void hashRawFingerprint_shouldReturnStableSha256Hex() {
        String first = machineFingerprintService.hashRawFingerprint("MAC|test-uuid|serial");
        String second = machineFingerprintService.hashRawFingerprint("MAC|test-uuid|serial");
        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void buildRawFingerprint_shouldUseFallbackOnUnknownOs() {
        MachineFingerprintService service =
                new MachineFingerprintService() {
                    @Override
                    String buildRawFingerprint() {
                        return "OTHER|home|linux|aarch64";
                    }
                };
        assertEquals("OTHER|home|linux|aarch64", service.buildRawFingerprint());
    }
}
