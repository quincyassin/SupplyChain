package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ExportSettingsDto;
import com.ecommerce.ordersplit.dto.PickExportDirectoryResponse;
import com.ecommerce.ordersplit.dto.SaveExportSettingsRequest;
import com.ecommerce.ordersplit.entity.ExportSettings;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ExportMode;
import com.ecommerce.ordersplit.repository.ExportSettingsRepository;
import com.ecommerce.ordersplit.util.ExportPathHelper;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导出配置服务
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ExportSettingsService {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExportSettingsRepository exportSettingsRepository;
    private final FolderPickerService folderPickerService;

    @Transactional(readOnly = true)
    public ExportSettingsDto getSettings() {
        return toDto(loadOrCreate());
    }

    @Transactional(readOnly = true)
    public ExportMode getCurrentMode() {
        return loadOrCreate().getMode();
    }

    @Transactional(readOnly = true)
    public Path getExportRootPath() {
        ExportSettings settings = loadOrCreate();
        Path exportRoot = ExportPathHelper.normalizeExportRoot(settings.getExportDirectory());
        ExportPathHelper.ensureExportRootWritable(exportRoot);
        return exportRoot;
    }

    @Transactional
    public ExportSettingsDto saveSettings(SaveExportSettingsRequest request) {
        if (request == null || request.getMode() == null) {
            throw new BusinessException("请选择导出方式");
        }
        ExportSettings settings = loadOrCreate();
        settings.setMode(request.getMode());
        if (request.getMode() == ExportMode.SERVER_DIRECTORY) {
            Path exportRoot =
                    ExportPathHelper.normalizeExportRoot(request.getExportDirectory());
            ExportPathHelper.ensureExportRootWritable(exportRoot);
            settings.setExportDirectory(exportRoot.toString());
        } else if (request.getExportDirectory() != null && !request.getExportDirectory().isBlank()) {
            Path exportRoot =
                    ExportPathHelper.normalizeExportRoot(request.getExportDirectory());
            ExportPathHelper.ensureExportRootWritable(exportRoot);
            settings.setExportDirectory(exportRoot.toString());
        }
        exportSettingsRepository.save(settings);
        return toDto(settings);
    }

    /**
     * 弹出本机文件夹选择器，返回选中的导出根目录（不自动保存，由前端确认后调用 save）
     */
    @Transactional(readOnly = true)
    public PickExportDirectoryResponse pickExportDirectory() {
        Path initialDirectory = resolveInitialPickDirectory();
        Optional<Path> picked =
                folderPickerService.pickDirectory(initialDirectory, "选择导出根目录");
        if (picked.isEmpty()) {
            return new PickExportDirectoryResponse(true, null);
        }
        Path normalized = ExportPathHelper.normalizeExportRoot(picked.get().toString());
        ExportPathHelper.ensureExportRootWritable(normalized);
        return new PickExportDirectoryResponse(false, normalized.toString());
    }

    private Path resolveInitialPickDirectory() {
        ExportSettings settings = loadOrCreate();
        if (settings.getExportDirectory() != null && !settings.getExportDirectory().isBlank()) {
            try {
                Path configured =
                        ExportPathHelper.normalizeExportRoot(settings.getExportDirectory());
                if (java.nio.file.Files.isDirectory(configured)) {
                    return configured;
                }
            } catch (BusinessException ignored) {
                // 使用默认目录作为初始位置
            }
        }
        return ExportPathHelper.resolveDefaultExportRoot();
    }

    private ExportSettings loadOrCreate() {
        return exportSettingsRepository
                .findById(ExportSettings.SINGLETON_ID)
                .orElseGet(this::createDefault);
    }

    private ExportSettings createDefault() {
        ExportSettings settings = new ExportSettings();
        settings.setId(ExportSettings.SINGLETON_ID);
        settings.setMode(ExportMode.SERVER_DIRECTORY);
        settings.setExportDirectory(ExportPathHelper.resolveDefaultExportRoot().toString());
        return exportSettingsRepository.save(settings);
    }

    private ExportSettingsDto toDto(ExportSettings settings) {
        String updatedAt =
                settings.getUpdatedAt() == null
                        ? null
                        : settings.getUpdatedAt().format(DISPLAY_TIME);
        String exportDirectory =
                settings.getExportDirectory() == null || settings.getExportDirectory().isBlank()
                        ? ExportPathHelper.resolveDefaultExportRoot().toString()
                        : settings.getExportDirectory().trim();
        return new ExportSettingsDto(settings.getMode(), exportDirectory, updatedAt);
    }
}
