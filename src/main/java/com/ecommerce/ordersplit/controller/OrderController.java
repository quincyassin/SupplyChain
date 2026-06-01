package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.AssignMerchantResult;
import com.ecommerce.ordersplit.dto.BatchReceiptRequest;
import com.ecommerce.ordersplit.dto.BatchReceiptResponse;
import com.ecommerce.ordersplit.dto.ExportSelectedRequest;
import com.ecommerce.ordersplit.dto.ImportedDateSummaryDto;
import com.ecommerce.ordersplit.dto.OrderFieldDto;
import com.ecommerce.ordersplit.dto.ReadHeadersResponse;
import com.ecommerce.ordersplit.dto.ReceiptExportResponse;
import com.ecommerce.ordersplit.dto.ReconcileExportRequest;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.dto.TaskResponse;
import com.ecommerce.ordersplit.dto.MarkAfterSalesRequest;
import com.ecommerce.ordersplit.dto.UpdateImportedOrderFieldsRequest;
import com.ecommerce.ordersplit.dto.UpdateOrderMerchantRequest;
import com.ecommerce.ordersplit.service.OrderProcessService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 订单按商家分单 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderProcessService orderProcessService;

    /**
     * 可映射字段列表（配置页使用，无需上传文件）
     */
    @GetMapping("/fields")
    public ResponseEntity<List<OrderFieldDto>> listFields() {
        return ResponseEntity.ok(orderProcessService.listOrderFields());
    }

    /**
     * 查询当日已入库订单（兼容旧接口）
     */
    @GetMapping("/imported/today")
    public ResponseEntity<SplitResultResponse> listTodayImported() {
        return ResponseEntity.ok(orderProcessService.listTodayImportedOrders());
    }

    /**
     * 最近 10 天每日分单条数，用于分单回单页左侧「分单日期」快捷筛选
     */
    @GetMapping("/imported/dates")
    public ResponseEntity<List<ImportedDateSummaryDto>> listImportedDates() {
        return ResponseEntity.ok(orderProcessService.listRecentImportedDateSummaries());
    }

    /**
     * 按分单日期或日期区间查询已入库订单（历史最多一年）
     */
    @GetMapping("/imported")
    public ResponseEntity<SplitResultResponse> listImportedByDate(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "merchant", required = false) String merchant,
            @RequestParam(value = "receiptStatus", required = false) String receiptStatus,
            @RequestParam(value = "afterSales", required = false) String afterSales,
            @RequestParam(value = "afterSalesStatus", required = false) String afterSalesStatus) {
        if (startDate != null || endDate != null) {
            LocalDate rangeStart = startDate != null ? startDate : endDate;
            LocalDate rangeEnd = endDate != null ? endDate : startDate;
            return ResponseEntity.ok(
                    orderProcessService.listImportedOrdersByDateRange(
                            rangeStart,
                            rangeEnd,
                            keyword,
                            platform,
                            merchant,
                            receiptStatus,
                            afterSales,
                            afterSalesStatus));
        }
        LocalDate queryDate = date == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : date;
        return ResponseEntity.ok(
                orderProcessService.listImportedOrdersByDate(
                        queryDate, keyword, platform, merchant, receiptStatus, afterSales, afterSalesStatus));
    }

    /**
     * 删除指定日期已入库的单条订单
     */
    @DeleteMapping("/imported/{systemNo}")
    public ResponseEntity<SplitResultResponse> deleteImported(
            @PathVariable String systemNo,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(orderProcessService.deleteImportedOrder(systemNo, date));
    }

    /**
     * 手动修改单条订单商家（不写入商家配置）
     */
    @PutMapping("/imported/{systemNo}/merchant")
    public ResponseEntity<SplitResultResponse> updateImportedMerchant(
            @PathVariable String systemNo,
            @RequestBody UpdateOrderMerchantRequest request,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(orderProcessService.updateImportedOrderMerchant(systemNo, date, request));
    }

    /**
     * 手动修改单条订单可编辑字段
     */
    @PutMapping("/imported/{systemNo}/fields")
    public ResponseEntity<SplitResultResponse> updateImportedFields(
            @PathVariable String systemNo,
            @RequestBody UpdateImportedOrderFieldsRequest request,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(orderProcessService.updateImportedOrderFields(systemNo, date, request));
    }

    /**
     * 标记单条订单需售后
     */
    @PutMapping("/imported/{systemNo}/after-sales")
    public ResponseEntity<SplitResultResponse> markImportedAfterSales(
            @PathVariable String systemNo,
            @RequestBody MarkAfterSalesRequest request,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(orderProcessService.markImportedOrderAfterSales(systemNo, date, request));
    }

    /**
     * 取消单条订单售后标记
     */
    @DeleteMapping("/imported/{systemNo}/after-sales")
    public ResponseEntity<Void> cancelImportedAfterSales(
            @PathVariable String systemNo,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        orderProcessService.cancelImportedOrderAfterSales(systemNo, date);
        return ResponseEntity.noContent().build();
    }

    /**
     * 标记单条订单售后完结
     */
    @PutMapping("/imported/{systemNo}/after-sales/complete")
    public ResponseEntity<Void> completeImportedAfterSales(
            @PathVariable String systemNo,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        orderProcessService.completeImportedOrderAfterSales(systemNo, date);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量维护回单（按系统单号匹配当日订单，更新物流信息与回单状态）
     */
    @PostMapping("/imported/receipt/batch")
    public ResponseEntity<BatchReceiptResponse> batchReceipt(
            @RequestBody BatchReceiptRequest request,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(orderProcessService.batchUpdateReceipt(request, date));
    }

    /**
     * 批量删除指定日期已入库订单
     */
    @PostMapping("/imported/delete-selected")
    public ResponseEntity<SplitResultResponse> deleteImportedSelected(
            @RequestBody ExportSelectedRequest request,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(orderProcessService.deleteImportedOrders(request, date));
    }

    /**
     * 读取 Excel 表头并返回推荐列映射
     */
    @PostMapping("/read-headers/suggest")
    public ResponseEntity<ReadHeadersResponse> suggestHeaders(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(orderProcessService.suggestHeaders(file));
    }

    @PostMapping("/read-headers")
    public ResponseEntity<ReadHeadersResponse> readHeaders(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(orderProcessService.readHeaders(file));
    }

    /**
     * 上传导入：匹配平台并按商家关键字分单入库（不导出 Excel）
     */
    @PostMapping("/import")
    public ResponseEntity<SplitResultResponse> importOrders(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mapping", required = false) String mapping) {
        return ResponseEntity.ok(orderProcessService.importByPlatform(file, mapping));
    }

    /**
     * 按商家分单：已有商家保留不动，其余按关键字分单；区间内全部订单参与导出（不传 file 时）
     */
    @PostMapping("/split")
    public ResponseEntity<?> split(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "mapping", required = false) String mapping,
            @RequestParam(value = "startDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(value = "date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @RequestParam(value = "platforms", required = false) List<String> platforms,
            @RequestParam(value = "merchants", required = false) List<String> merchants) {
        if (file != null && !file.isEmpty()) {
            return ResponseEntity.ok(orderProcessService.splitByMerchant(file, mapping));
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate resolvedStart = startDate != null ? startDate : (date != null ? date : today);
        LocalDate resolvedEnd = endDate != null ? endDate : resolvedStart;
        return ResponseEntity.ok(
                orderProcessService.assignPendingMerchantsForRange(
                        resolvedStart, resolvedEnd, platforms, merchants));
    }

    /**
     * 导出勾选的订单行（单个 Excel）
     */
    @PostMapping("/export/selected")
    public ResponseEntity<Resource> exportSelected(@RequestBody ExportSelectedRequest request) {
        return orderProcessService.exportSelectedOrders(request);
    }

    /**
     * 按平台模板导出勾选订单（表头与列序与模板 Excel 一致）
     */
    @PostMapping("/export/platform-template/selected")
    public ResponseEntity<Resource> exportPlatformTemplateSelected(
            @RequestBody ExportSelectedRequest request) {
        return orderProcessService.exportSelectedOrdersByPlatformTemplate(request);
    }

    /**
     * 商家对账导出
     */
    @PostMapping("/export/reconcile/merchant")
    public ResponseEntity<Resource> exportMerchantReconcile(
            @RequestBody ReconcileExportRequest request) {
        return orderProcessService.exportMerchantReconcile(request);
    }

    /**
     * 平台对账导出
     */
    @PostMapping("/export/reconcile/platform")
    public ResponseEntity<Resource> exportPlatformReconcile(
            @RequestBody ReconcileExportRequest request) {
        return orderProcessService.exportPlatformReconcile(request);
    }

    /**
     * 按商家分单结果打包 ZIP（浏览器下载）
     */
    @GetMapping("/export/split-by-merchant")
    public ResponseEntity<Resource> exportSplitByMerchant(
            @RequestParam(value = "exportDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate exportDate,
            @RequestParam(value = "systemNos", required = false) List<String> systemNos,
            @RequestParam(value = "downloadToken", required = false) String downloadToken) {
        return orderProcessService.exportSplitByMerchant(exportDate, systemNos, downloadToken);
    }

    /**
     * 打开分单导出目录（testData/{exportDate}/分单/）
     */
    @PostMapping("/export/open-split-directory")
    public ResponseEntity<Void> openSplitExportDirectory(
            @RequestParam("exportDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exportDate) {
        orderProcessService.openSplitExportDirectory(exportDate);
        return ResponseEntity.noContent().build();
    }

    /**
     * 打开平台模版导出目录（testData/{exportDate}/回单/）
     */
    @PostMapping("/export/open-receipt-directory")
    public ResponseEntity<Void> openReceiptExportDirectory(
            @RequestParam("exportDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate exportDate) {
        orderProcessService.openReceiptExportDirectory(exportDate);
        return ResponseEntity.noContent().build();
    }

    /**
     * @deprecated 使用 {@link #openReceiptExportDirectory(LocalDate)}
     */
    @Deprecated
    @PostMapping("/export/open-receipt-directories")
    public ResponseEntity<Void> openReceiptExportDirectories(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        orderProcessService.openReceiptExportDirectories(startDate, endDate);
        return ResponseEntity.noContent().build();
    }

    /**
     * 按商家回单导出（写盘或返回文件数）
     */
    @PostMapping("/export/receipt-by-merchant")
    public ResponseEntity<ReceiptExportResponse> exportReceiptByMerchant(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "platforms", required = false) List<String> platforms) {
        return ResponseEntity.ok(orderProcessService.exportReceiptByMerchant(startDate, endDate, platforms));
    }

    /**
     * 按商家回单打包 ZIP（浏览器下载）
     */
    @GetMapping("/export/receipt-by-merchant")
    public ResponseEntity<Resource> downloadReceiptByMerchantZip(
            @RequestParam(value = "startDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @RequestParam(value = "downloadToken", required = false) String downloadToken,
            @RequestParam(value = "platforms", required = false) List<String> platforms) {
        return orderProcessService.exportReceiptByMerchantZip(
                startDate, endDate, downloadToken, platforms);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskResponse>> listTasks() {
        return ResponseEntity.ok(orderProcessService.listRecentTasks());
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(orderProcessService.getTask(id));
    }
}
