package com.ecommerce.ordersplit.util;

/**
 * SQL LIKE 通配符转义
 *
 * @author huangxinsong
 */
public final class SqlLikeUtil {

    private SqlLikeUtil() {}

    /**
     * 生成包含匹配用的 LIKE 模式（前后 %）
     */
    public static String toContainsPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + escape(keyword.trim()) + "%";
    }

    /**
     * 转义 LIKE 特殊字符 % 与 _
     */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
