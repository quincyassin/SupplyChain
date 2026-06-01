package com.ecommerce.ordersplit.util;

import com.ecommerce.ordersplit.exception.BusinessException;

/**
 * 导入时校验收货人电话列是否疑似误映射（如绑到了地址/备注列）
 *
 * @author huangxinsong
 */
public final class PhoneImportValidator {

    /** 与 import_order.phone VARCHAR(32) 一致 */
    public static final int PHONE_MAX_LENGTH = 32;

    private static final int SUSPICIOUS_LENGTH = 20;

    private PhoneImportValidator() {}

    /**
     * @param rawPhone Excel 原始单元格内容
     * @param excelRowNum Excel 行号（含表头，与文件一致）
     */
    public static void validateImportValue(String rawPhone, int excelRowNum) {
        if (rawPhone == null || rawPhone.isBlank() || excelRowNum <= 0) {
            return;
        }
        String trimmed = rawPhone.trim();
        if (trimmed.length() > PHONE_MAX_LENGTH) {
            throw new BusinessException(
                    "第 "
                            + excelRowNum
                            + " 行「收货人电话」不能超过 "
                            + PHONE_MAX_LENGTH
                            + " 个字符（当前 "
                            + trimmed.length()
                            + " 字），请核对表头映射或精简 Excel 内容");
        }
        if (trimmed.length() <= SUSPICIOUS_LENGTH) {
            return;
        }
        if (!looksLikeMisMappedContent(trimmed)) {
            return;
        }
        throw new BusinessException(
                "第 "
                        + excelRowNum
                        + " 行「收货人电话」内容疑似地址或备注（"
                        + trimmed.length()
                        + " 字），请核对系统配置 → 表头映射中「收货人电话」绑定的 Excel 列是否正确");
    }

    static boolean looksLikeMisMappedContent(String value) {
        if (containsAddressKeyword(value)) {
            return true;
        }
        long digitCount = value.chars().filter(Character::isDigit).count();
        return value.length() > 30 && digitCount < 8;
    }

    private static boolean containsAddressKeyword(String value) {
        return value.contains("省")
                || value.contains("市")
                || value.contains("区")
                || value.contains("县")
                || value.contains("镇")
                || value.contains("乡")
                || value.contains("街道")
                || value.contains("路")
                || value.contains("号")
                || value.contains("室")
                || value.contains("栋")
                || value.contains("单元");
    }
}
