package com.ecommerce.ordersplit.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 物流单号多值分隔工具（支持中文逗号、英文逗号）
 *
 * @author huangxinsong
 */
public final class LogisticsNoUtil {

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[,，]");

    private LogisticsNoUtil() {
    }

    /** 将多个物流单号合并为英文逗号分隔的存储格式 */
    public static String join(List<String> logisticsNumbers) {
        if (logisticsNumbers == null || logisticsNumbers.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String number : logisticsNumbers) {
            if (number == null) {
                continue;
            }
            String trimmed = number.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return String.join(",", normalized);
    }

    /** 按中文或英文逗号拆分为多个物流单号 */
    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : SEPARATOR_PATTERN.split(raw)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 归一化：接受中英文逗号输入，统一存为英文逗号分隔 */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (!containsSeparator(trimmed)) {
            return trimmed;
        }
        return join(split(trimmed));
    }

    public static boolean containsSeparator(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.indexOf(',') >= 0 || value.indexOf('，') >= 0;
    }
}
