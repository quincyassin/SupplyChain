package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.ExportSelectedRequest;
import com.ecommerce.ordersplit.dto.ImportOrderRecycleBinOperationResultDto;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.service.ImportOrderRecycleBinService;
import java.time.LocalDate;
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
 * 订单回收站 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders/recycle-bin")
@RequiredArgsConstructor
public class ImportOrderRecycleBinController {

    private final ImportOrderRecycleBinService importOrderRecycleBinService;

    @GetMapping
    public ResponseEntity<SplitResultResponse> listRecycleBinOrders(
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
                importOrderRecycleBinService.listRecycleBinOrdersByDateRange(
                        startDate,
                        endDate,
                        keyword,
                        platform,
                        merchant,
                        receiptStatus,
                        afterSales,
                        afterSalesStatus));
    }

    @PostMapping("/restore-selected")
    public ResponseEntity<ImportOrderRecycleBinOperationResultDto> restoreSelected(
            @RequestBody ExportSelectedRequest request) {
        if (request == null || request.getSystemNos() == null || request.getSystemNos().isEmpty()) {
            throw new BusinessException("请先勾选要恢复的订单");
        }
        return ResponseEntity.ok(importOrderRecycleBinService.restoreSelected(request.getSystemNos()));
    }

    @PostMapping("/purge-selected")
    public ResponseEntity<ImportOrderRecycleBinOperationResultDto> purgeSelected(
            @RequestBody ExportSelectedRequest request) {
        if (request == null || request.getSystemNos() == null || request.getSystemNos().isEmpty()) {
            throw new BusinessException("请先勾选要彻底删除的订单");
        }
        return ResponseEntity.ok(importOrderRecycleBinService.purgeSelected(request.getSystemNos()));
    }
}
