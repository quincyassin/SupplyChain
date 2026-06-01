package com.ecommerce.ordersplit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ecommerce.ordersplit.license.LicenseCodec;
import com.ecommerce.ordersplit.license.LicensePayload;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;

class LicenseServiceTest {

    private static final String MACHINE_ID = "d".repeat(64);

    @TempDir
    Path tempDir;

    private MachineFingerprintService machineFingerprintService;
    private LicenseService licenseService;
    private PrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        machineFingerprintService = Mockito.mock(MachineFingerprintService.class);
        when(machineFingerprintService.currentMachineId()).thenReturn(MACHINE_ID);
        when(machineFingerprintService.formatCurrentMachineId())
                .thenReturn(LicenseCodec.formatMachineId(MACHINE_ID));
        when(machineFingerprintService.currentPlatform()).thenReturn("MAC");

        ClassPathResource privateKeyResource = new ClassPathResource("license/test-private.pem");
        privateKey =
                LicenseCodec.loadPrivateKey(
                        LicenseCodec.readPemContent(
                                new String(
                                        privateKeyResource.getInputStream().readAllBytes(),
                                        StandardCharsets.UTF_8)));

        Path licenseFile = tempDir.resolve("license.dat");
        licenseService =
                new LicenseService(
                        machineFingerprintService,
                        true,
                        licenseFile.toString(),
                        new ClassPathResource("license/test-public.pem"));
        licenseService.ensureLicenseDirectory();
    }

    @Test
    void activate_shouldPersistAndReturnLicensedStatus() {
        String activationCode =
                LicenseCodec.sign(
                        new LicensePayload(MACHINE_ID, LocalDate.now().plusDays(30)), privateKey);

        var status = licenseService.activate(activationCode);

        assertTrue(status.isLicensed());
        assertEquals(MACHINE_ID, status.getMachineId());
        assertTrue(licenseService.isLicensed());
    }

    @Test
    void activate_shouldRejectMismatchedMachineId() {
        String activationCode =
                LicenseCodec.sign(
                        new LicensePayload("e".repeat(64), LocalDate.now().plusDays(30)), privateKey);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.ecommerce.ordersplit.exception.BusinessException.class,
                () -> licenseService.activate(activationCode));
    }

    @Test
    void getStatus_shouldReportUnlicensedWhenFileMissing() {
        var status = licenseService.getStatus();
        assertFalse(status.isLicensed());
        assertEquals(MACHINE_ID, status.getMachineId());
    }

    @Test
    void isLicensed_shouldRejectExpiredLicense() throws Exception {
        String activationCode =
                LicenseCodec.sign(
                        new LicensePayload(MACHINE_ID, LocalDate.now().minusDays(1)), privateKey);
        Path licenseFile = tempDir.resolve("license.dat");
        Files.writeString(licenseFile, activationCode, StandardCharsets.UTF_8);

        assertFalse(licenseService.isLicensed());
    }
}
