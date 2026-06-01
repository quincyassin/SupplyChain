package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.LicenseStatusDto;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.license.LicenseCodec;
import com.ecommerce.ordersplit.license.LicensePayload;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 离线激活码授权
 *
 * @author huangxinsong
 */
@Service
public class LicenseService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final MachineFingerprintService machineFingerprintService;
    private final boolean enforced;
    private final Path licenseFilePath;
    private final PublicKey publicKey;

    public LicenseService(
            MachineFingerprintService machineFingerprintService,
            @Value("${app.license.enforced:true}") boolean enforced,
            @Value("${app.license.file-path:./data/license/license.dat}") String licenseFilePath,
            @Value("classpath:license/license-public.pem") Resource publicKeyResource) {
        this.machineFingerprintService = machineFingerprintService;
        this.enforced = enforced;
        this.licenseFilePath = Paths.get(licenseFilePath).toAbsolutePath().normalize();
        this.publicKey = loadPublicKey(publicKeyResource);
    }

    @PostConstruct
    void ensureLicenseDirectory() {
        try {
            Path parent = licenseFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ex) {
            throw new BusinessException("无法创建授权目录：" + ex.getMessage());
        }
    }

    public LicenseStatusDto getStatus() {
        String machineId = machineFingerprintService.currentMachineId();
        String machineIdDisplay = machineFingerprintService.formatCurrentMachineId();
        String platform = machineFingerprintService.currentPlatform();
        if (!enforced) {
            return LicenseStatusDto.builder()
                    .licensed(true)
                    .enforced(false)
                    .machineId(machineId)
                    .machineIdDisplay(machineIdDisplay)
                    .platform(platform)
                    .message("开发模式未启用授权校验")
                    .build();
        }
        LicenseValidationResult validationResult = validateStoredLicense(machineId);
        if (!validationResult.licensed()) {
            return LicenseStatusDto.builder()
                    .licensed(false)
                    .enforced(true)
                    .machineId(machineId)
                    .machineIdDisplay(machineIdDisplay)
                    .platform(platform)
                    .message(validationResult.message())
                    .build();
        }
        return buildLicensedStatus(machineId, machineIdDisplay, platform, validationResult.payload());
    }

    public LicenseStatusDto activate(String activationCode) {
        if (!enforced) {
            return getStatus();
        }
        String machineId = machineFingerprintService.currentMachineId();
        LicensePayload payload = LicenseCodec.verifyAndParse(activationCode, publicKey);
        if (!payload.machineId().equals(machineId)) {
            throw new BusinessException("激活码与当前机器不匹配");
        }
        assertNotExpired(payload.expireAt());
        saveActivationCode(LicenseCodec.normalizeActivationCode(activationCode));
        return buildLicensedStatus(
                machineId,
                machineFingerprintService.formatCurrentMachineId(),
                machineFingerprintService.currentPlatform(),
                payload);
    }

    public boolean isLicensed() {
        if (!enforced) {
            return true;
        }
        String machineId = machineFingerprintService.currentMachineId();
        return validateStoredLicense(machineId).licensed();
    }

    private LicenseValidationResult validateStoredLicense(String machineId) {
        if (!Files.isRegularFile(licenseFilePath)) {
            return LicenseValidationResult.failure("软件尚未激活，请先输入激活码");
        }
        try {
            String storedCode = Files.readString(licenseFilePath, StandardCharsets.UTF_8).trim();
            if (storedCode.isEmpty()) {
                return LicenseValidationResult.failure("授权文件无效，请重新激活");
            }
            LicensePayload payload = LicenseCodec.verifyAndParse(storedCode, publicKey);
            if (!payload.machineId().equals(machineId)) {
                return LicenseValidationResult.failure("授权与当前机器不匹配，请联系管理员重新发码");
            }
            if (isExpired(payload.expireAt())) {
                return LicenseValidationResult.failure("授权已过期，请联系管理员续期");
            }
            return LicenseValidationResult.success(payload);
        } catch (BusinessException ex) {
            return LicenseValidationResult.failure(ex.getMessage());
        } catch (IOException ex) {
            return LicenseValidationResult.failure("读取授权文件失败");
        }
    }

    private LicenseStatusDto buildLicensedStatus(
            String machineId, String machineIdDisplay, String platform, LicensePayload payload) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        long remainingDays = ChronoUnit.DAYS.between(today, payload.expireAt());
        return LicenseStatusDto.builder()
                .licensed(true)
                .enforced(enforced)
                .machineId(machineId)
                .machineIdDisplay(machineIdDisplay)
                .platform(platform)
                .expireAt(payload.expireAt().toString())
                .remainingDays(Math.max(remainingDays, 0))
                .message("授权有效")
                .build();
    }

    private void saveActivationCode(String activationCode) {
        try {
            Path parent = licenseFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(licenseFilePath, activationCode, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException("保存授权文件失败：" + ex.getMessage());
        }
    }

    private void assertNotExpired(LocalDate expireAt) {
        if (isExpired(expireAt)) {
            throw new BusinessException("激活码已过期");
        }
    }

    private boolean isExpired(LocalDate expireAt) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        return today.isAfter(expireAt);
    }

    private PublicKey loadPublicKey(Resource publicKeyResource) {
        try {
            String pem = new String(publicKeyResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return LicenseCodec.loadPublicKey(LicenseCodec.readPemContent(pem));
        } catch (IOException ex) {
            throw new BusinessException("加载授权公钥失败");
        }
    }

    private record LicenseValidationResult(boolean licensed, LicensePayload payload, String message) {

        static LicenseValidationResult success(LicensePayload payload) {
            return new LicenseValidationResult(true, payload, "授权有效");
        }

        static LicenseValidationResult failure(String message) {
            return new LicenseValidationResult(false, null, message);
        }
    }
}
