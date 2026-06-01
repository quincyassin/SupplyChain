package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.ArchiveImportOrdersRequest;
import com.ecommerce.ordersplit.dto.ImportOrderArchiveOperationResultDto;
import com.ecommerce.ordersplit.dto.ImportOrderArchivePreviewDto;
import com.ecommerce.ordersplit.dto.ImportedDateSummaryDto;
import com.ecommerce.ordersplit.dto.RestoreImportOrdersRequest;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.service.ImportOrderArchiveService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单物理归档 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders/archive")
@RequiredArgsConstructor
public class ImportOrderArchiveController {

    private final ImportOrderArchiveService importOrderArchiveService;

    @GetMapping("/preview")
    public ResponseEntity<ImportOrderArchivePreviewDto> previewArchive(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(importOrderArchiveService.previewArchive(startDate, endDate));
    }

    @PostMapping
    public ResponseEntity<ImportOrderArchiveOperationResultDto> archiveOrders(
            @RequestBody ArchiveImportOrdersRequest request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                importOrderArchiveService.archiveDateRange(
                        request.getStartDate(), request.getEndDate()));
    }

    @GetMapping("/dates")
    public ResponseEntity<List<ImportedDateSummaryDto>> listArchivedDates() {
        return ResponseEntity.ok(importOrderArchiveService.listArchivedDateSummaries());
    }

    @GetMapping("/imported")
    public ResponseEntity<SplitResultResponse> listArchivedOrders(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "merchant", required = false) String merchant,
            @RequestParam(value = "receiptStatus", required = false) String receiptStatus,
            @RequestParam(value = "afterSales", required = false) String afterSales,
            @RequestParam(value = "afterSalesStatus", required = false) String afterSalesStatus) {
        return ResponseEntity.ok(
                importOrderArchiveService.listArchivedOrdersByDateRange(
                        startDate,
                        endDate,
                        keyword,
                        platform,
                        merchant,
                        receiptStatus,
                        afterSales,
                        afterSalesStatus));
    }

    @GetMapping("/restore/preview")
    public ResponseEntity<ImportOrderArchivePreviewDto> previewRestore(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(importOrderArchiveService.previewRestore(startDate, endDate));
    }

    @PostMapping("/restore")
    public ResponseEntity<ImportOrderArchiveOperationResultDto> restoreOrders(
            @RequestBody RestoreImportOrdersRequest request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                importOrderArchiveService.restoreDateRange(
                        request.getStartDate(), request.getEndDate()));
    }
}
