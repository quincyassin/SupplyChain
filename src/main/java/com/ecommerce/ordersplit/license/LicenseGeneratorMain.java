package com.ecommerce.ordersplit.license;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 管理员离线发码工具
 *
 * <p>用法：
 *
 * <pre>
 * java ... LicenseGeneratorMain --private-key /path/to/license-private.pem \
 *   --machine-id &lt;64位hex&gt; --expire 2027-12-31
 * </pre>
 *
 * @author huangxinsong
 */
public final class LicenseGeneratorMain {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private LicenseGeneratorMain() {}

    public static void main(String[] args) {
        try {
            Arguments arguments = parseArguments(args);
            String pem = Files.readString(arguments.privateKeyPath(), StandardCharsets.UTF_8);
            PrivateKey privateKey = LicenseCodec.loadPrivateKey(LicenseCodec.readPemContent(pem));
            String machineId = LicenseCodec.normalizeMachineId(arguments.machineId());
            LocalDate expireAt = parseExpireAt(arguments.expireAt());
            LicensePayload payload = new LicensePayload(machineId, expireAt);
            String activationCode = LicenseCodec.sign(payload, privateKey);
            System.out.println("machineId=" + machineId);
            System.out.println("expireAt=" + DATE_FORMAT.format(expireAt));
            System.out.println("activationCode=" + activationCode);
            System.out.println("activationCodeDisplay=" + LicenseCodec.formatForDisplay(activationCode));
        } catch (BusinessException ex) {
            System.err.println("发码失败: " + ex.getMessage());
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("发码失败: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static Arguments parseArguments(String[] args) {
        String privateKeyPath = null;
        String machineId = null;
        String expireAt = null;
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--private-key" -> privateKeyPath = requireValue(args, ++index, "--private-key");
                case "--machine-id" -> machineId = requireValue(args, ++index, "--machine-id");
                case "--expire" -> expireAt = requireValue(args, ++index, "--expire");
                default -> throw new BusinessException("未知参数: " + arg);
            }
        }
        if (privateKeyPath == null || machineId == null || expireAt == null) {
            throw new BusinessException(
                    "参数不完整，示例: --private-key /path/to/license-private.pem "
                            + "--machine-id <64位hex> --expire 2027-12-31");
        }
        return new Arguments(Path.of(privateKeyPath), machineId, expireAt);
    }

    private static String requireValue(String[] args, int valueIndex, String optionName) {
        if (valueIndex >= args.length) {
            throw new BusinessException("缺少参数值: " + optionName);
        }
        return args[valueIndex];
    }

    private static LocalDate parseExpireAt(String expireAt) {
        try {
            return LocalDate.parse(expireAt.trim(), DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("到期日格式无效，应为 yyyy-MM-dd");
        }
    }

    private record Arguments(Path privateKeyPath, String machineId, String expireAt) {}
}
