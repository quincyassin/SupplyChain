package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 导出下载缓存测试
 *
 * @author huangxinsong
 */
class ExportDownloadCacheServiceTest {

    @Test
    void storeAndTake_shouldReturnSameZipBytes() {
        ExportDownloadCacheService cacheService = new ExportDownloadCacheService();
        byte[] zipBytes = new byte[] {1, 2, 3};

        String token = cacheService.store("分单导出_2026-05-31.zip", zipBytes);
        ExportDownloadCacheService.CachedExport cached = cacheService.take(token);

        assertArrayEquals(zipBytes, cached.zipBytes());
    }

    @Test
    void take_shouldRejectUnknownToken() {
        ExportDownloadCacheService cacheService = new ExportDownloadCacheService();
        assertThrows(BusinessException.class, () -> cacheService.take("unknown-token"));
    }
}
