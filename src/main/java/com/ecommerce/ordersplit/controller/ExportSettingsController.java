package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.ExportSettingsDto;
import com.ecommerce.ordersplit.dto.PickExportDirectoryResponse;
import com.ecommerce.ordersplit.dto.SaveExportSettingsRequest;
import com.ecommerce.ordersplit.service.ExportSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出配置 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders/export-settings")
@RequiredArgsConstructor
public class ExportSettingsController {

    private final ExportSettingsService exportSettingsService;

    @GetMapping
    public ResponseEntity<ExportSettingsDto> getSettings() {
        return ResponseEntity.ok(exportSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<ExportSettingsDto> saveSettings(
            @RequestBody SaveExportSettingsRequest request) {
        return ResponseEntity.ok(exportSettingsService.saveSettings(request));
    }

    /** 弹出本机文件夹选择器（仅桌面 standalone 环境可用） */
    @PostMapping("/pick-directory")
    public ResponseEntity<PickExportDirectoryResponse> pickExportDirectory() {
        return ResponseEntity.ok(exportSettingsService.pickExportDirectory());
    }
}
