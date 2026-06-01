package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.license.LicenseCodec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 跨平台机器指纹（Windows / macOS）
 *
 * @author huangxinsong
 */
@Service
public class MachineFingerprintService {

    private static final Pattern MACHINE_GUID_PATTERN =
            Pattern.compile("MachineGuid\\s+REG_SZ\\s+([0-9a-fA-F-]{36})");
    private static final Pattern IO_PLATFORM_UUID_PATTERN =
            Pattern.compile("\"IOPlatformUUID\"\\s=\\s\"([0-9A-Fa-f-]{36})\"");
    private static final Pattern HARDWARE_SERIAL_PATTERN =
            Pattern.compile("Serial Number \\(system\\):\\s*(.+)", Pattern.CASE_INSENSITIVE);

    public String currentMachineId() {
        return hashRawFingerprint(buildRawFingerprint());
    }

    public String currentPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "WIN";
        }
        if (osName.contains("mac")) {
            return "MAC";
        }
        return "OTHER";
    }

    String buildRawFingerprint() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return buildWindowsRawFingerprint();
        }
        if (osName.contains("mac")) {
            return buildMacRawFingerprint();
        }
        return buildFallbackRawFingerprint();
    }

    String hashRawFingerprint(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException("机器码计算失败");
        }
    }

    private String buildWindowsRawFingerprint() {
        String machineGuid = readWindowsMachineGuid();
        String volumeSerial = readWindowsVolumeSerial();
        return "WIN|" + machineGuid + "|" + volumeSerial;
    }

    private String buildMacRawFingerprint() {
        String platformUuid = readMacPlatformUuid();
        String serialNumber = readMacHardwareSerial();
        if (serialNumber.isBlank()) {
            return "MAC|" + platformUuid;
        }
        return "MAC|" + platformUuid + "|" + serialNumber.trim();
    }

    private String buildFallbackRawFingerprint() {
        String home = System.getProperty("user.home", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        return "OTHER|" + home + "|" + osName + "|" + osArch;
    }

    private String readWindowsMachineGuid() {
        ProcessBuilder builder =
                new ProcessBuilder(
                        "reg",
                        "query",
                        "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                        "/v",
                        "MachineGuid");
        String output = runCommand(builder, "读取 Windows MachineGuid 失败");
        Matcher matcher = MACHINE_GUID_PATTERN.matcher(output);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        throw new BusinessException("无法读取 Windows 机器标识");
    }

    private String readWindowsVolumeSerial() {
        ProcessBuilder builder =
                new ProcessBuilder(
                        "wmic",
                        "logicaldisk",
                        "where",
                        "DeviceID='C:'",
                        "get",
                        "VolumeSerialNumber");
        String output = runCommand(builder, "读取 Windows 卷序列号失败");
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("VolumeSerialNumber")) {
                return trimmed;
            }
        }
        throw new BusinessException("无法读取 Windows 卷序列号");
    }

    private String readMacPlatformUuid() {
        ProcessBuilder builder =
                new ProcessBuilder("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
        String output = runCommand(builder, "读取 macOS IOPlatformUUID 失败");
        Matcher matcher = IO_PLATFORM_UUID_PATTERN.matcher(output);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        throw new BusinessException("无法读取 macOS 机器标识");
    }

    private String readMacHardwareSerial() {
        ProcessBuilder builder =
                new ProcessBuilder("system_profiler", "SPHardwareDataType");
        try {
            String output = runCommand(builder, "读取 macOS 硬件序列号失败");
            Matcher matcher = HARDWARE_SERIAL_PATTERN.matcher(output);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            return "";
        } catch (BusinessException ex) {
            return "";
        }
    }

    private String runCommand(ProcessBuilder builder, String errorMessage) {
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BusinessException(errorMessage);
            }
            if (output.isBlank()) {
                throw new BusinessException(errorMessage);
            }
            return output;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(errorMessage);
        }
    }

    public String formatCurrentMachineId() {
        return LicenseCodec.formatMachineId(currentMachineId());
    }
}
