package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.SaveExportSettingsRequest;
import com.ecommerce.ordersplit.entity.ExportSettings;
import com.ecommerce.ordersplit.model.ExportMode;
import com.ecommerce.ordersplit.repository.ExportSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导出配置服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ExportSettingsServiceTest {

    @Mock private ExportSettingsRepository exportSettingsRepository;

    private ExportSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ExportSettingsService(exportSettingsRepository);
    }

    @Test
    void getCurrentMode_shouldReturnDefaultWhenMissing() {
        when(exportSettingsRepository.findById(ExportSettings.SINGLETON_ID))
                .thenReturn(Optional.empty());
        when(exportSettingsRepository.save(any(ExportSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExportMode mode = service.getCurrentMode();

        assertEquals(ExportMode.SERVER_DIRECTORY, mode);
    }

    @Test
    void saveSettings_shouldPersistSelectedMode() {
        ExportSettings existing = new ExportSettings();
        existing.setId(ExportSettings.SINGLETON_ID);
        existing.setMode(ExportMode.SERVER_DIRECTORY);
        when(exportSettingsRepository.findById(ExportSettings.SINGLETON_ID))
                .thenReturn(Optional.of(existing));
        when(exportSettingsRepository.save(any(ExportSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SaveExportSettingsRequest request = new SaveExportSettingsRequest();
        request.setMode(ExportMode.BROWSER_DOWNLOAD);
        service.saveSettings(request);

        ArgumentCaptor<ExportSettings> captor = ArgumentCaptor.forClass(ExportSettings.class);
        verify(exportSettingsRepository).save(captor.capture());
        assertEquals(ExportMode.BROWSER_DOWNLOAD, captor.getValue().getMode());
    }
}
