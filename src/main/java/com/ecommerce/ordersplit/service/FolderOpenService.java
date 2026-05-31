package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;

/**
 * 在服务器本机打开指定文件夹（仅限桌面 testData 导出目录）
 *
 * @author huangxinsong
 */
@Service
public class FolderOpenService {

    private static final String EXPORT_ROOT_DIR = "testData";

    /**
     * 打开指定目录；路径必须在 {@link #resolveExportRoot()} 之下
     */
    public void openDirectory(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        assertUnderAllowedExportRoot(normalized);
        if (!Files.isDirectory(normalized)) {
            throw new BusinessException("导出目录不存在：" + normalized);
        }
        if (tryOpenWithDesktop(normalized)) {
            return;
        }
        openDirectoryViaProcess(normalized);
    }

    /**
     * 桌面 testData 根目录（绝对路径）
     */
    Path resolveExportRoot() {
        Path desktop = Paths.get(System.getProperty("user.home"), "Desktop");
        if (!Files.isDirectory(desktop)) {
            throw new BusinessException("未找到桌面目录，无法打开导出文件夹");
        }
        return desktop.resolve(EXPORT_ROOT_DIR).toAbsolutePath().normalize();
    }

    private void assertUnderAllowedExportRoot(Path directory) {
        Path exportRoot = resolveExportRoot();
        if (!directory.startsWith(exportRoot)) {
            throw new BusinessException("不允许打开该路径");
        }
    }

    private boolean tryOpenWithDesktop(Path directory) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
                return true;
            }
        } catch (HeadlessException | IOException ignored) {
            // 无图形环境或 Desktop API 不可用时走进程兜底
        }
        return false;
    }

    private void openDirectoryViaProcess(Path directory) {
        String os = System.getProperty("os.name", "").toLowerCase();
        ProcessBuilder builder;
        if (os.contains("win")) {
            builder = new ProcessBuilder("explorer", directory.toString());
        } else if (os.contains("mac")) {
            builder = new ProcessBuilder("open", directory.toString());
        } else {
            builder = new ProcessBuilder("xdg-open", directory.toString());
        }
        try {
            builder.start();
        } catch (IOException ex) {
            throw new BusinessException("无法打开文件夹：" + ex.getMessage());
        }
    }
}
