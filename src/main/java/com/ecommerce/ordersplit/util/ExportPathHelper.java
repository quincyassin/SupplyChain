package com.ecommerce.ordersplit.util;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 导出目录路径工具
 *
 * @author huangxinsong
 */
public final class ExportPathHelper {

    private static final String LEGACY_EXPORT_FOLDER = "testData";

    private ExportPathHelper() {
    }

    /** 默认导出根目录：桌面 testData（兼容旧版行为） */
    public static Path resolveDefaultExportRoot() {
        Path desktop = Paths.get(System.getProperty("user.home"), "Desktop");
        if (Files.isDirectory(desktop)) {
            return desktop.resolve(LEGACY_EXPORT_FOLDER).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), LEGACY_EXPORT_FOLDER)
                .toAbsolutePath()
                .normalize();
    }

    public static Path normalizeExportRoot(String raw) {
        if (raw == null || raw.isBlank()) {
            return resolveDefaultExportRoot();
        }
        String trimmed = raw.trim();
        if (trimmed.contains("\0")) {
            throw new BusinessException("导出目录路径不合法");
        }
        Path path = Paths.get(trimmed).toAbsolutePath().normalize();
        if (!path.isAbsolute()) {
            throw new BusinessException("导出目录必须为绝对路径");
        }
        return path;
    }

    public static void ensureExportRootWritable(Path exportRoot) {
        try {
            Files.createDirectories(exportRoot);
        } catch (IOException ex) {
            throw new BusinessException("无法创建导出目录：" + ex.getMessage());
        }
        if (!Files.isDirectory(exportRoot)) {
            throw new BusinessException("导出路径不是有效目录：" + exportRoot);
        }
        if (!Files.isWritable(exportRoot)) {
            throw new BusinessException("导出目录不可写：" + exportRoot);
        }
    }
}
