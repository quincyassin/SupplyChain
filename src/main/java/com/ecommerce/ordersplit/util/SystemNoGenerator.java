package com.ecommerce.ordersplit.util;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 系统编号生成与识别（10 位雪花 ID，兼容历史 NanoId / UUID / SYS 格式）
 *
 * @author huangxinsong
 */
public final class SystemNoGenerator {

    /** 新格式：10 位数字雪花 ID */
    private static final Pattern SNOWFLAKE_PATTERN = Pattern.compile("^\\d{10}$");

    /** 历史 16 位 NanoId */
    private static final Pattern NANO_ID_PATTERN = Pattern.compile("^[0-9A-Za-z]{16}$");

    private static final Pattern UUID_PATTERN =
            Pattern.compile(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern LEGACY_SYSTEM_NO_PATTERN =
            Pattern.compile("SYS-\\d{8}-\\d{6}-\\d{2}", Pattern.CASE_INSENSITIVE);

    private SystemNoGenerator() {}

    public static String generate() {
        return SnowflakeIdGenerator.getInstance().nextSystemNo();
    }

    /**
     * 批量生成系统编号（导入场景使用）
     */
    public static List<String> generateBatch(int count) {
        return SnowflakeIdGenerator.getInstance().nextSystemNos(count);
    }

    public static String normalize(String systemNo) {
        if (systemNo == null) {
            return "";
        }
        String trimmed = systemNo.trim();
        if (LEGACY_SYSTEM_NO_PATTERN.matcher(trimmed).matches()) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    public static String requireValid(String systemNo) {
        String normalized = normalize(systemNo);
        if (normalized.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        if (!isSystemNoFormat(normalized)) {
            throw new BusinessException("系统编号格式不正确");
        }
        return normalized;
    }

    public static boolean isSystemNoFormat(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalize(value);
        return SNOWFLAKE_PATTERN.matcher(normalized).matches()
                || NANO_ID_PATTERN.matcher(normalized).matches()
                || UUID_PATTERN.matcher(normalized).matches()
                || LEGACY_SYSTEM_NO_PATTERN.matcher(normalized).matches();
    }

    public static boolean isLegacySystemNoFormat(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalize(value);
        return LEGACY_SYSTEM_NO_PATTERN.matcher(normalized).matches()
                || NANO_ID_PATTERN.matcher(normalized).matches()
                || UUID_PATTERN.matcher(normalized).matches();
    }

    /**
     * 列表展示用系统编号
     */
    public static String display(ImportOrder order) {
        if (order == null || order.getSystemNo() == null || order.getSystemNo().isBlank()) {
            return "";
        }
        return normalize(order.getSystemNo());
    }

    /**
     * 回单匹配键（与 {@link #display} 一致）
     */
    public static String matchKey(ImportOrder order) {
        return display(order);
    }
}
