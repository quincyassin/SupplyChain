package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.springframework.stereotype.Service;

/**
 * 本机文件夹选择器：Windows 桌面优先 Swing；macOS 用 osascript；无图形环境时 Windows 兜底 PowerShell。
 *
 * @author huangxinsong
 */
@Service
public class FolderPickerService {

    private static final String APP_NAME = "分单发单助手";

    /** PowerShell 文件夹对话框用户取消时的退出码 */
    static final int WINDOWS_PICK_CANCEL_EXIT_CODE = 2;

    enum NativePickStatus {
        SELECTED,
        CANCELLED,
        UNAVAILABLE
    }

    record NativePickOutcome(NativePickStatus status, Path path) {}

    /**
     * 弹出系统文件夹选择对话框
     *
     * @param initialDirectory 初始打开目录，可为 null
     * @param dialogTitle 对话框标题
     * @return 选中目录；用户取消则 empty
     */
    public Optional<Path> pickDirectory(Path initialDirectory, String dialogTitle) {
        String title = normalizeTitle(dialogTitle);
        if (isWindows() && !GraphicsEnvironment.isHeadless()) {
            return pickDirectoryOnWindowsDesktop(initialDirectory, title);
        }
        NativePickOutcome nativeOutcome = tryNativeFolderPicker(initialDirectory, title);
        if (nativeOutcome.status() == NativePickStatus.CANCELLED) {
            return Optional.empty();
        }
        if (nativeOutcome.status() == NativePickStatus.SELECTED && nativeOutcome.path() != null) {
            return Optional.of(nativeOutcome.path());
        }
        if (!GraphicsEnvironment.isHeadless()) {
            return pickDirectoryWithJavaUi(initialDirectory, title);
        }
        throw new BusinessException("无法打开文件夹选择器，请确认在本机已登录图形界面");
    }

    /**
     * Windows 桌面环境：优先同进程 Swing 对话框（javaw 下更可靠），失败时 PowerShell 兜底。
     */
    private Optional<Path> pickDirectoryOnWindowsDesktop(Path initialDirectory, String title) {
        try {
            return pickDirectoryWithJavaUi(initialDirectory, title);
        } catch (BusinessException ex) {
            NativePickOutcome fallback = pickDirectoryWithWindowsPowerShell(initialDirectory, title);
            if (fallback.status() == NativePickStatus.CANCELLED) {
                return Optional.empty();
            }
            if (fallback.status() == NativePickStatus.SELECTED && fallback.path() != null) {
                return Optional.of(fallback.path());
            }
            throw ex;
        }
    }

    private NativePickOutcome tryNativeFolderPicker(Path initialDirectory, String title) {
        if (isMacOs()) {
            return pickDirectoryWithMacOsScript(initialDirectory, title);
        }
        if (isWindows()) {
            return pickDirectoryWithWindowsPowerShell(initialDirectory, title);
        }
        return new NativePickOutcome(NativePickStatus.UNAVAILABLE, null);
    }

    private NativePickOutcome pickDirectoryWithMacOsScript(Path initialDirectory, String title) {
        try {
            Process process =
                    new ProcessBuilder(
                                    "osascript",
                                    "-e",
                                    buildMacOsChooseFolderScript(initialDirectory, title))
                            .redirectErrorStream(true)
                            .start();
            ProcessOutput output = readProcessOutput(process);
            if (output.exitCode() == 0 && !output.text().isBlank()) {
                return new NativePickOutcome(
                        NativePickStatus.SELECTED, normalizePickedPath(output.text()));
            }
            if (isUserCancelled(output.text())) {
                return new NativePickOutcome(NativePickStatus.CANCELLED, null);
            }
            return new NativePickOutcome(NativePickStatus.UNAVAILABLE, null);
        } catch (IOException ex) {
            return new NativePickOutcome(NativePickStatus.UNAVAILABLE, null);
        }
    }

    private NativePickOutcome pickDirectoryWithWindowsPowerShell(Path initialDirectory, String title) {
        try {
            Process process =
                    new ProcessBuilder(
                                    "powershell",
                                    "-NoProfile",
                                    "-STA",
                                    "-Command",
                                    buildWindowsChooseFolderCommand(initialDirectory, title))
                            .redirectErrorStream(true)
                            .start();
            ProcessOutput output = readProcessOutput(process);
            return resolveWindowsPowerShellOutcome(output.exitCode(), output.text());
        } catch (IOException ex) {
            return new NativePickOutcome(NativePickStatus.UNAVAILABLE, null);
        }
    }

    static NativePickOutcome resolveWindowsPowerShellOutcome(int exitCode, String output) {
        if (exitCode == WINDOWS_PICK_CANCEL_EXIT_CODE) {
            return new NativePickOutcome(NativePickStatus.CANCELLED, null);
        }
        if (exitCode == 0 && output != null && !output.isBlank()) {
            return new NativePickOutcome(
                    NativePickStatus.SELECTED, normalizePickedPath(output));
        }
        return new NativePickOutcome(NativePickStatus.UNAVAILABLE, null);
    }

    static String buildMacOsChooseFolderScript(Path initialDirectory, String title) {
        String escapedTitle = escapeAppleScriptString(title);
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            String escapedPath =
                    escapeAppleScriptString(
                            initialDirectory.toAbsolutePath().normalize().toString());
            return String.format(
                    "POSIX path of (choose folder with prompt \"%s\" default location (POSIX file \"%s\"))",
                    escapedTitle, escapedPath);
        }
        return String.format("POSIX path of (choose folder with prompt \"%s\")", escapedTitle);
    }

    static String buildWindowsChooseFolderCommand(Path initialDirectory, String title) {
        StringBuilder command = new StringBuilder();
        command.append("[System.Windows.Forms.Application]::EnableVisualStyles(); ");
        command.append("Add-Type -AssemblyName System.Windows.Forms; ");
        command.append("$dialog = New-Object System.Windows.Forms.FolderBrowserDialog; ");
        command.append("$dialog.Description = '").append(escapePowerShellSingleQuoted(title)).append("'; ");
        command.append("$dialog.ShowNewFolderButton = $true; ");
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            command
                    .append("$dialog.SelectedPath = '")
                    .append(escapePowerShellSingleQuoted(initialDirectory.toAbsolutePath().normalize().toString()))
                    .append("'; ");
        }
        command.append("$result = $dialog.ShowDialog(); ");
        command.append(
                "if ($result -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $dialog.SelectedPath; exit 0 }; ");
        command.append("exit ").append(WINDOWS_PICK_CANCEL_EXIT_CODE);
        return command.toString();
    }

    static String escapeAppleScriptString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String escapePowerShellSingleQuoted(String value) {
        return value.replace("'", "''");
    }

    static boolean isUserCancelled(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.contains("user canceled")
                || normalized.contains("user cancelled")
                || normalized.contains("(-128)")
                || normalized.contains("-128");
    }

    private Optional<Path> pickDirectoryWithJavaUi(Path initialDirectory, String title) {
        AtomicReference<Optional<Path>> selected = new AtomicReference<>(Optional.empty());
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            if (isMacOs()) {
                                selected.set(pickDirectoryOnMacAwt(initialDirectory, title));
                            } else {
                                selected.set(pickDirectoryWithJFileChooser(initialDirectory, title));
                            }
                        } catch (RuntimeException ex) {
                            error.set(ex);
                        }
                    });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("打开文件夹选择器被中断");
        } catch (Exception ex) {
            throw new BusinessException("打开文件夹选择器失败：" + ex.getMessage());
        }
        if (error.get() != null) {
            throw error.get();
        }
        return selected.get();
    }

    private Optional<Path> pickDirectoryOnMacAwt(Path initialDirectory, String title) {
        try {
            configureMacAwtProperties();
            FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
            if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
                dialog.setDirectory(initialDirectory.toFile().getAbsolutePath());
            }
            dialog.setVisible(true);
            return resolveMacFileDialogSelection(dialog.getDirectory(), dialog.getFile());
        } catch (HeadlessException ex) {
            throw new BusinessException("当前环境无法打开文件夹选择器，请确认在本机图形界面下运行");
        }
    }

    static Optional<Path> resolveMacFileDialogSelection(String directory, String file) {
        if (file == null || file.isBlank()) {
            return Optional.empty();
        }
        Path direct = Path.of(file);
        if (direct.isAbsolute() && Files.isDirectory(direct)) {
            return Optional.of(direct.normalize());
        }
        if (directory == null || directory.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(directory, file).toAbsolutePath().normalize());
    }

    private Optional<Path> pickDirectoryWithJFileChooser(Path initialDirectory, String title) {
        Frame owner = createDialogOwnerFrame();
        try {
            applySystemLookAndFeel();
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setDialogType(JFileChooser.OPEN_DIALOG);
            if (isWindows()) {
                chooser.putClientProperty("JFileChooser.useSystemExtensionHiding", true);
            }
            if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
                chooser.setCurrentDirectory(initialDirectory.toFile());
            }
            int result = chooser.showOpenDialog(owner);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return Optional.empty();
            }
            return Optional.of(chooser.getSelectedFile().toPath().toAbsolutePath().normalize());
        } catch (HeadlessException ex) {
            throw new BusinessException("当前环境无法打开文件夹选择器，请确认在本机图形界面下运行");
        } finally {
            owner.dispose();
        }
    }

    /** 提供置顶父窗口，避免对话框被浏览器遮挡 */
    private Frame createDialogOwnerFrame() {
        Frame frame = new Frame();
        frame.setUndecorated(true);
        frame.setSize(0, 0);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        return frame;
    }

    private static Path normalizePickedPath(String rawPath) {
        String trimmed = rawPath == null ? "" : rawPath.trim();
        if (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return Path.of(trimmed).toAbsolutePath().normalize();
    }

    private ProcessOutput readProcessOutput(Process process) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String text = reader.lines().collect(Collectors.joining(System.lineSeparator())).trim();
            int exitCode = process.waitFor();
            return new ProcessOutput(exitCode, text);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("打开文件夹选择器被中断");
        }
    }

    private record ProcessOutput(int exitCode, String text) {}

    private void configureMacAwtProperties() {
        System.setProperty("apple.awt.application.name", APP_NAME);
        System.setProperty("apple.awt.fileDialogForDirectories", "true");
    }

    private void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 使用默认 L&F
        }
    }

    private String normalizeTitle(String dialogTitle) {
        return dialogTitle == null || dialogTitle.isBlank() ? "选择文件夹" : dialogTitle.trim();
    }

    private boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
