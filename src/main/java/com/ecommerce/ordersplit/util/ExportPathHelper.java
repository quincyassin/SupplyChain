package com.ecommerce.ordersplit.util;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

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

    /**
     * 导出根目录下的日期层级目录：{年}/{月}/{日}，例如 2026/05/30
     */
    public static Path resolveDateDirectory(Path exportRoot, LocalDate date) {
        return exportRoot
                .resolve(String.valueOf(date.getYear()))
                .resolve(String.format("%02d", date.getMonthValue()))
                .resolve(String.format("%02d", date.getDayOfMonth()));
    }

    /**
     * ZIP 内相对路径前缀（正斜杠分隔），与 {@link #resolveDateDirectory} 一致
     */
    public static String formatDateFolderRelativePath(LocalDate date) {
        return date.getYear()
                + "/"
                + String.format("%02d", date.getMonthValue())
                + "/"
                + String.format("%02d", date.getDayOfMonth());
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
