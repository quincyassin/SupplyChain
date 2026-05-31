package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 文件夹打开服务测试
 *
 * @author huangxinsong
 */
class FolderOpenServiceTest {

    @TempDir
    Path tempDir;

    private Path desktopDir;
    private Path exportRoot;
    private FolderOpenService folderOpenService;

    @BeforeEach
    void setUp() throws Exception {
        desktopDir = tempDir.resolve("Desktop");
        Files.createDirectories(desktopDir);
        exportRoot = desktopDir.resolve("testData");
        Files.createDirectories(exportRoot);

        folderOpenService = new FolderOpenService() {
            @Override
            Path resolveExportRoot() {
                return exportRoot.toAbsolutePath().normalize();
            }
        };
    }

    @Test
    void openDirectory_shouldRejectPathOutsideExportRoot() throws Exception {
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> folderOpenService.openDirectory(outside));
        assertEquals("不允许打开该路径", ex.getMessage());
    }

    @Test
    void openDirectory_shouldRejectMissingDirectory() throws Exception {
        Path missing = exportRoot.resolve("2026-05-29");

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> folderOpenService.openDirectory(missing));
        assertEquals("导出目录不存在：" + missing.toAbsolutePath().normalize(), ex.getMessage());
    }

    @Test
    void openDirectory_shouldAllowExportDateDirectory() throws Exception {
        Path dateDir = exportRoot.resolve("2026-05-29");
        Files.createDirectories(dateDir);

        // 测试环境无 GUI，走 ProcessBuilder 兜底；只要不抛业务异常即可
        assertDoesNotThrow(() -> folderOpenService.openDirectory(dateDir));
    }
}
