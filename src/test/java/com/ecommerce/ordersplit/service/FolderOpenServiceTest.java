package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 文件夹打开服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class FolderOpenServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ExportSettingsService exportSettingsService;

    private Path exportRoot;
    private FolderOpenService folderOpenService;

    @BeforeEach
    void setUp() throws Exception {
        exportRoot = tempDir.resolve("exports").toAbsolutePath().normalize();
        Files.createDirectories(exportRoot);
        when(exportSettingsService.getExportRootPath()).thenReturn(exportRoot);
        folderOpenService = new FolderOpenService(exportSettingsService);
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
    void openDirectory_shouldRejectMissingDirectory() {
        Path missing = exportRoot.resolve("2026-05-29");

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> folderOpenService.openDirectory(missing));
        assertEquals("导出目录不存在：" + missing, ex.getMessage());
    }

    @Test
    void openDirectory_shouldAllowExportDateDirectory() throws Exception {
        Path dateDir = exportRoot.resolve("2026-05-29").resolve("分单");
        Files.createDirectories(dateDir);

        assertDoesNotThrow(() -> folderOpenService.openDirectory(dateDir));
    }
}
