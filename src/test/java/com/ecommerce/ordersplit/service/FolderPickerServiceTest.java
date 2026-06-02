package com.ecommerce.ordersplit.service;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件夹选择器测试
 *
 * @author huangxinsong
 */
class FolderPickerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveMacFileDialogSelection_shouldReturnEmptyWhenCancelled() {
        assertTrue(FolderPickerService.resolveMacFileDialogSelection("/tmp/", null).isEmpty());
        assertTrue(FolderPickerService.resolveMacFileDialogSelection(null, null).isEmpty());
    }

    @Test
    void resolveMacFileDialogSelection_shouldCombineDirectoryAndName() {
        var path =
                FolderPickerService.resolveMacFileDialogSelection(
                        tempDir.toString() + java.io.File.separator, "exports");

        assertEquals(tempDir.resolve("exports").toAbsolutePath().normalize(), path.orElseThrow());
    }

    @Test
    void resolveMacFileDialogSelection_shouldAcceptAbsolutePath() {
        var path = FolderPickerService.resolveMacFileDialogSelection(null, tempDir.toString());

        assertEquals(tempDir.toAbsolutePath().normalize(), path.orElseThrow());
    }

    @Test
    void buildMacOsChooseFolderScript_shouldIncludeDefaultLocation() {
        String script =
                FolderPickerService.buildMacOsChooseFolderScript(tempDir, "选择导出根目录");

        assertTrue(script.contains("choose folder with prompt \"选择导出根目录\""));
        assertTrue(script.contains("default location"));
        assertTrue(script.contains(tempDir.toAbsolutePath().normalize().toString()));
    }

    @Test
    void buildMacOsChooseFolderScript_shouldEscapeQuotesInTitle() {
        String script = FolderPickerService.buildMacOsChooseFolderScript(null, "选\"目录");

        assertTrue(script.contains("选\\\"目录"));
    }

    @Test
    void isUserCancelled_shouldDetectMacCancelMessage() {
        assertTrue(FolderPickerService.isUserCancelled("User canceled."));
        assertTrue(FolderPickerService.isUserCancelled("error -128"));
        assertFalse(FolderPickerService.isUserCancelled("permission denied"));
    }

    @Test
    void buildWindowsChooseFolderCommand_shouldIncludeSelectedPath() {
        String command =
                FolderPickerService.buildWindowsChooseFolderCommand(
                        tempDir, "选择导出根目录");

        assertTrue(command.contains("EnableVisualStyles"));
        assertTrue(command.contains("FolderBrowserDialog"));
        assertTrue(command.contains("选择导出根目录"));
        assertTrue(command.contains(tempDir.toAbsolutePath().normalize().toString()));
        assertTrue(command.contains("exit " + FolderPickerService.WINDOWS_PICK_CANCEL_EXIT_CODE));
    }

    @Test
    void buildWindowsChooseFolderCommand_shouldSkipMissingInitialDirectory() {
        Path missingDir = tempDir.resolve("not-exists");

        String command =
                FolderPickerService.buildWindowsChooseFolderCommand(missingDir, "选择目录");

        assertFalse(command.contains("$dialog.SelectedPath ="));
    }

    @Test
    void resolveWindowsPowerShellOutcome_shouldDetectCancel() {
        var outcome =
                FolderPickerService.resolveWindowsPowerShellOutcome(
                        FolderPickerService.WINDOWS_PICK_CANCEL_EXIT_CODE, "");

        assertEquals(FolderPickerService.NativePickStatus.CANCELLED, outcome.status());
    }

    @Test
    void resolveWindowsPowerShellOutcome_shouldReturnSelectedPath() {
        var outcome =
                FolderPickerService.resolveWindowsPowerShellOutcome(
                        0, tempDir.toAbsolutePath().normalize().toString());

        assertEquals(FolderPickerService.NativePickStatus.SELECTED, outcome.status());
        assertEquals(tempDir.toAbsolutePath().normalize(), outcome.path());
    }
}
