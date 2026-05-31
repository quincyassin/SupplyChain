package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ExportSettingsDto;
import com.ecommerce.ordersplit.dto.SaveExportSettingsRequest;
import com.ecommerce.ordersplit.entity.ExportSettings;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ExportMode;
import com.ecommerce.ordersplit.repository.ExportSettingsRepository;
import java.time.format.DateTimeFormatter;
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

    @Transactional(readOnly = true)
    public ExportSettingsDto getSettings() {
        return toDto(loadOrCreate());
    }

    @Transactional(readOnly = true)
    public ExportMode getCurrentMode() {
        return loadOrCreate().getMode();
    }

    @Transactional
    public ExportSettingsDto saveSettings(SaveExportSettingsRequest request) {
        if (request == null || request.getMode() == null) {
            throw new BusinessException("请选择导出方式");
        }
        ExportSettings settings = loadOrCreate();
        settings.setMode(request.getMode());
        exportSettingsRepository.save(settings);
        return toDto(settings);
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
        return exportSettingsRepository.save(settings);
    }

    private ExportSettingsDto toDto(ExportSettings settings) {
        String updatedAt =
                settings.getUpdatedAt() == null
                        ? null
                        : settings.getUpdatedAt().format(DISPLAY_TIME);
        return new ExportSettingsDto(settings.getMode(), updatedAt);
    }
}
