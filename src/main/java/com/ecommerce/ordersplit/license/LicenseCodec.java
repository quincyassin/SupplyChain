package com.ecommerce.ordersplit.license;

import com.ecommerce.ordersplit.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 激活码编解码与 RSA 验签
 *
 * @author huangxinsong
 */
public final class LicenseCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final Pattern MACHINE_ID_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private LicenseCodec() {}

    public static String toCanonicalJson(LicensePayload payload) {
        validateMachineId(payload.machineId());
        return String.format(
                "{\"v\":%d,\"machineId\":\"%s\",\"expireAt\":\"%s\"}",
                payload.version(), payload.machineId(), DATE_FORMAT.format(payload.expireAt()));
    }

    public static LicensePayload parsePayloadJson(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            int version = root.path("v").asInt(0);
            String machineId = normalizeMachineId(root.path("machineId").asText(""));
            String expireAtText = root.path("expireAt").asText("");
            if (version != LicensePayload.CURRENT_VERSION) {
                throw new BusinessException("激活码版本不受支持");
            }
            LocalDate expireAt = parseExpireAt(expireAtText);
            return new LicensePayload(version, machineId, expireAt);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("激活码内容无效");
        }
    }

    public static String sign(LicensePayload payload, PrivateKey privateKey) {
        String canonicalJson = toCanonicalJson(payload);
        byte[] signatureBytes = signBytes(canonicalJson.getBytes(StandardCharsets.UTF_8), privateKey);
        String payloadPart = Base64.getEncoder().encodeToString(canonicalJson.getBytes(StandardCharsets.UTF_8));
        String signaturePart = Base64.getEncoder().encodeToString(signatureBytes);
        return payloadPart + "." + signaturePart;
    }

    public static LicensePayload verifyAndParse(String activationCode, PublicKey publicKey) {
        String normalized = normalizeActivationCode(activationCode);
        int separatorIndex = normalized.indexOf('.');
        if (separatorIndex <= 0 || separatorIndex >= normalized.length() - 1) {
            throw new BusinessException("激活码格式不正确");
        }
        String payloadPart = normalized.substring(0, separatorIndex);
        String signaturePart = normalized.substring(separatorIndex + 1);
        byte[] payloadBytes = decodeBase64(payloadPart, "激活码载荷无效");
        byte[] signatureBytes = decodeBase64(signaturePart, "激活码签名无效");
        verifySignature(payloadBytes, signatureBytes, publicKey);
        return parsePayloadJson(new String(payloadBytes, StandardCharsets.UTF_8));
    }

    public static String formatForDisplay(String activationCode) {
        String normalized = normalizeActivationCode(activationCode);
        int separatorIndex = normalized.indexOf('.');
        if (separatorIndex <= 0) {
            return groupText(normalized);
        }
        return groupText(normalized.substring(0, separatorIndex))
                + "."
                + groupText(normalized.substring(separatorIndex + 1));
    }

    public static String normalizeActivationCode(String activationCode) {
        if (activationCode == null) {
            return "";
        }
        return activationCode
                .trim()
                .replace("-", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                // 微信、邮件等渠道粘贴时，Base64 中的 + 常被变成空格
                .replace(" ", "+");
    }

    public static String normalizeMachineId(String machineId) {
        if (machineId == null) {
            throw new BusinessException("机器码无效");
        }
        String normalized = machineId.trim().toLowerCase().replace("-", "").replace(" ", "");
        if (!MACHINE_ID_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException("机器码格式不正确");
        }
        return normalized;
    }

    public static String formatMachineId(String machineId) {
        String normalized = normalizeMachineId(machineId);
        StringBuilder builder = new StringBuilder(79);
        for (int index = 0; index < normalized.length(); index += 4) {
            if (index > 0) {
                builder.append('-');
            }
            builder.append(normalized, index, Math.min(index + 4, normalized.length()));
        }
        return builder.toString();
    }

    public static PublicKey loadPublicKey(byte[] keyBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("公钥加载失败");
        }
    }

    public static PrivateKey loadPrivateKey(byte[] keyBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("私钥加载失败");
        }
    }

    public static byte[] readPemContent(String pemText) {
        String normalized = pemText
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        if (normalized.isEmpty()) {
            throw new BusinessException("密钥文件内容为空");
        }
        return Base64.getDecoder().decode(normalized);
    }

    private static LocalDate parseExpireAt(String expireAtText) {
        if (expireAtText == null || expireAtText.isBlank()) {
            throw new BusinessException("激活码缺少到期日");
        }
        try {
            return LocalDate.parse(expireAtText.trim(), DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("激活码到期日格式无效");
        }
    }

    private static void validateMachineId(String machineId) {
        normalizeMachineId(machineId);
    }

    private static byte[] decodeBase64(String value, String errorMessage) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(errorMessage);
        }
    }

    private static byte[] signBytes(byte[] payloadBytes, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payloadBytes);
            return signature.sign();
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("激活码签名失败");
        }
    }

    private static void verifySignature(byte[] payloadBytes, byte[] signatureBytes, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            if (!signature.verify(signatureBytes)) {
                throw new BusinessException("激活码签名校验失败");
            }
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("激活码签名校验失败");
        }
    }

    private static String groupText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + value.length() / 4);
        for (int index = 0; index < value.length(); index += 4) {
            if (index > 0) {
                builder.append('-');
            }
            builder.append(value, index, Math.min(index + 4, value.length()));
        }
        return builder.toString();
    }
}
