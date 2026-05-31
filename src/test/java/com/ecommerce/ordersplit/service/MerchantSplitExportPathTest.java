package com.ecommerce.ordersplit.service;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商家导出路径与文件名测试
 *
 * @author huangxinsong
 */
class MerchantSplitExportPathTest {

    @Test
    void buildPlatformDateFileName_shouldUsePlatformAndIsoDate() {
        LocalDate date = LocalDate.of(2026, 5, 30);
        assertEquals(
                "客莱拿-2026-05-30.xlsx",
                MerchantSplitExportService.buildPlatformDateFileName("客莱拿", date));
    }

    @Test
    void buildMerchantDateFileName_shouldUseMerchantAndIsoDate() {
        LocalDate date = LocalDate.of(2026, 5, 30);
        assertEquals("商家A-2026-05-30.xlsx", MerchantSplitExportService.buildMerchantDateFileName("商家A", date));
    }

    @Test
    void sanitizeFileName_shouldReplaceInvalidCharacters() {
        assertEquals("商家_A", MerchantSplitExportService.sanitizeFileName("商家/A"));
    }
}
