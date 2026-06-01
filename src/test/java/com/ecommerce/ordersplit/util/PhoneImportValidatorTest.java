package com.ecommerce.ordersplit.util;

import com.ecommerce.ordersplit.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收货人电话导入校验测试
 *
 * @author huangxinsong
 */
class PhoneImportValidatorTest {

    @Test
    void validateImportValue_shouldRejectOverlongPhone() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                PhoneImportValidator.validateImportValue(
                                        "1".repeat(33), 8));
        assertTrue(ex.getMessage().contains("第 8 行"));
        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    void validateImportValue_shouldAllowMultiplePhoneNumbers() {
        assertDoesNotThrow(
                () ->
                        PhoneImportValidator.validateImportValue(
                                "13812345678,13912345678901", 5));
    }

    @Test
    void validateImportValue_shouldRejectAddressLikeContent() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                PhoneImportValidator.validateImportValue(
                                        "广东省深圳市南山区科技园南路88号A座1201室", 5));
        assertTrue(ex.getMessage().contains("第 5 行"));
        assertTrue(ex.getMessage().contains("表头映射"));
    }

    @Test
    void looksLikeMisMappedContent_shouldDetectAddressKeywords() {
        assertTrue(
                PhoneImportValidator.looksLikeMisMappedContent(
                        "上海市浦东新区张江路1000号"));
        assertFalse(PhoneImportValidator.looksLikeMisMappedContent("13812345678,13987654321"));
    }
}
