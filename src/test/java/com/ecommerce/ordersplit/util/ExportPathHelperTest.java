package com.ecommerce.ordersplit.util;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出目录路径工具测试
 *
 * @author huangxinsong
 */
class ExportPathHelperTest {

    @Test
    void normalizeExportRoot_shouldUseDefaultWhenBlank() {
        Path path = ExportPathHelper.normalizeExportRoot("  ");
        assertTrue(path.toString().endsWith("testData"));
    }

    @Test
    void normalizeExportRoot_shouldNormalizeAbsolutePath() {
        Path path = ExportPathHelper.normalizeExportRoot("/tmp/custom-export");
        assertTrue(path.toString().replace('\\', '/').endsWith("/custom-export"));
    }

    @Test
    void normalizeExportRoot_shouldRejectNullByte() {
        assertThrows(
                com.ecommerce.ordersplit.exception.BusinessException.class,
                () -> ExportPathHelper.normalizeExportRoot("/tmp/bad\0path"));
    }
}
