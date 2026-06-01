package com.ecommerce.ordersplit.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LicenseCodecTest {

    private static PrivateKey privateKey;
    private static java.security.PublicKey publicKey;

    @BeforeAll
    static void loadKeys() throws Exception {
        ClassPathResource privateKeyResource = new ClassPathResource("license/test-private.pem");
        ClassPathResource publicKeyResource = new ClassPathResource("license/test-public.pem");
        privateKey =
                LicenseCodec.loadPrivateKey(
                        LicenseCodec.readPemContent(
                                new String(
                                        privateKeyResource.getInputStream().readAllBytes(),
                                        StandardCharsets.UTF_8)));
        publicKey =
                LicenseCodec.loadPublicKey(
                        LicenseCodec.readPemContent(
                                new String(
                                        publicKeyResource.getInputStream().readAllBytes(),
                                        StandardCharsets.UTF_8)));
    }

    @Test
    void signAndVerify_shouldRoundTripPayload() {
        String machineId = "a".repeat(64);
        LicensePayload payload = new LicensePayload(machineId, LocalDate.of(2027, 12, 31));
        String activationCode = LicenseCodec.sign(payload, privateKey);
        LicensePayload parsed = LicenseCodec.verifyAndParse(activationCode, publicKey);
        assertEquals(machineId, parsed.machineId());
        assertEquals(LocalDate.of(2027, 12, 31), parsed.expireAt());
    }

    @Test
    void verifyAndParse_shouldAcceptFormattedCode() {
        String machineId = "b".repeat(64);
        LicensePayload payload = new LicensePayload(machineId, LocalDate.of(2026, 6, 30));
        String activationCode = LicenseCodec.sign(payload, privateKey);
        String formatted = LicenseCodec.formatForDisplay(activationCode);
        LicensePayload parsed = LicenseCodec.verifyAndParse(formatted, publicKey);
        assertEquals(machineId, parsed.machineId());
    }

    @Test
    void verifyAndParse_shouldRejectTamperedPayload() {
        String machineId = "c".repeat(64);
        LicensePayload payload = new LicensePayload(machineId, LocalDate.of(2026, 1, 1));
        String activationCode = LicenseCodec.sign(payload, privateKey);
        String tampered = activationCode.replace('A', 'B');
        assertThrows(BusinessException.class, () -> LicenseCodec.verifyAndParse(tampered, publicKey));
    }

    @Test
    void verifyAndParse_shouldRecoverPlusSignFromSpaces() {
        String machineId = "d".repeat(64);
        LicensePayload payload = new LicensePayload(machineId, LocalDate.of(2026, 6, 1));
        String activationCode = LicenseCodec.sign(payload, privateKey);
        String corrupted = activationCode.replace('+', ' ');
        LicensePayload parsed = LicenseCodec.verifyAndParse(corrupted, publicKey);
        assertEquals(machineId, parsed.machineId());
    }

    @Test
    void normalizeMachineId_shouldRemoveDashes() {
        String normalized = LicenseCodec.normalizeMachineId("abcd-" + "0".repeat(60));
        assertEquals("abcd" + "0".repeat(60), normalized);
    }
}
