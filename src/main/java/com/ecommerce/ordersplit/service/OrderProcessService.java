package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.AfterSalesExportRequest;
import com.ecommerce.ordersplit.dto.AssignMerchantResult;
import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.dto.ParsedImportExcel;
import com.ecommerce.ordersplit.dto.BatchReceiptRequest;
import com.ecommerce.ordersplit.dto.BatchReceiptResponse;
import com.ecommerce.ordersplit.dto.ExportSelectedRequest;
import com.ecommerce.ordersplit.dto.ImportedDateSummaryDto;
import com.ecommerce.ordersplit.dto.OrderFieldDto;
import com.ecommerce.ordersplit.dto.ReadHeadersResponse;
import com.ecommerce.ordersplit.dto.PlatformExportTemplateDto;
import com.ecommerce.ordersplit.dto.ReceiptExportResponse;
import com.ecommerce.ordersplit.dto.ReconcileExportRequest;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.dto.TaskResponse;
import com.ecommerce.ordersplit.dto.MarkAfterSalesRequest;
import com.ecommerce.ordersplit.dto.UpdateImportedOrderFieldsRequest;
import com.ecommerce.ordersplit.dto.UpdateOrderMerchantRequest;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ProcessTask;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ExportMode;
import com.ecommerce.ordersplit.model.OperationType;
import com.ecommerce.ordersplit.model.OrderRow;
import com.ecommerce.ordersplit.model.TaskStatus;
import com.ecommerce.ordersplit.repository.ProcessTaskRepository;
import com.ecommerce.ordersplit.util.ContentDispositionUtil;
import com.ecommerce.ordersplit.service.MerchantSplitExportService.PreparedSplitExport;
import com.ecommerce.ordersplit.service.MerchantSplitExportService.PreparedReceiptExport;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 订单处理编排服务
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class OrderProcessService {

    private final ExcelParserService excelParserService;
    private final ExcelWriterService excelWriterService;
    private final OrderSplitMergeService orderSplitMergeService;
    private final ProcessTaskRepository processTaskRepository;
    private final ColumnMappingService columnMappingService;
    private final DailyTableService dailyTableService;
    private final ImportOrderPersistenceService importOrderPersistenceService;
    private final ImportOrderQueryService importOrderQueryService;
    private final ImportOrderReceiptService importOrderReceiptService;
    private final PlatformMappingTemplateService platformMappingTemplateService;
    private final MerchantConfigService merchantConfigService;
    private final MerchantSplitExportService merchantSplitExportService;
    private final ExportSettingsService exportSettingsService;
    private final FolderOpenService folderOpenService;
    private final ExportDownloadCacheService exportDownloadCacheService;
    private final ReconcileExportService reconcileExportService;
    private final AfterSalesExportService afterSalesExportService;

    public List<OrderFieldDto> listOrderFields() {
        return columnMappingService.listFields();
    }

    /** 配置页上传模板时使用（自动匹配，不要求已保存平台模板） */
    public ReadHeadersResponse suggestHeaders(MultipartFile file) {
        List<ExcelHeaderDto> headers =
                columnMappingService.ensureLogisticsTemplateHeaders(
                        excelParserService.readHeaders(file));
        ColumnMappingConfig suggested = columnMappingService.suggestMappingFromHeaders(headers);
        return new ReadHeadersResponse(
                headers,
                columnMappingService.toDtos(suggested),
                columnMappingService.listFields(),
                null);
    }

    public ReadHeadersResponse readHeaders(MultipartFile file) {
        List<ExcelHeaderDto> headers = excelParserService.readHeaders(file);
        TemplateHeaderMatch match = platformMappingTemplateService.matchByHeaders(headers);
        return new ReadHeadersResponse(
                headers,
                columnMappingService.toDtos(match.mapping()),
                columnMappingService.listFields(),
                match.platform());
    }

    /**
     * 查询当日已入库订单（页面刷新后恢复表格）
     */
    @Transactional(readOnly = true)
    public SplitResultResponse listTodayImportedOrders() {
        return importOrderQueryService.listTodayOrders(null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDate(LocalDate date) {
        return listImportedOrdersByDate(date, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDate(LocalDate date, String keyword) {
        return importOrderQueryService.listOrdersByDate(date, null, keyword);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDate(
            LocalDate date,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus) {
        return importOrderQueryService.listOrdersByDate(
                date, null, keyword, platform, merchant, receiptStatus, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDate(
            LocalDate date,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales) {
        return importOrderQueryService.listOrdersByDate(
                date, null, keyword, platform, merchant, receiptStatus, afterSales, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDate(
            LocalDate date,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales,
            String afterSalesStatus) {
        return importOrderQueryService.listOrdersByDate(
                date, null, keyword, platform, merchant, receiptStatus, afterSales, afterSalesStatus);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDateRange(LocalDate startDate, LocalDate endDate) {
        return listImportedOrdersByDateRange(startDate, endDate, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDateRange(
            LocalDate startDate, LocalDate endDate, String keyword) {
        return listImportedOrdersByDateRange(startDate, endDate, keyword, null, null, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus) {
        return importOrderQueryService.listOrdersByDateRange(
                startDate, endDate, null, keyword, platform, merchant, receiptStatus, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales) {
        return importOrderQueryService.listOrdersByDateRange(
                startDate,
                endDate,
                null,
                keyword,
                platform,
                merchant,
                receiptStatus,
                afterSales,
                null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listImportedOrdersByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales,
            String afterSalesStatus) {
        return importOrderQueryService.listOrdersByDateRange(
                startDate,
                endDate,
                null,
                keyword,
                platform,
                merchant,
                receiptStatus,
                afterSales,
                afterSalesStatus);
    }

    @Transactional(readOnly = true)
    public List<ImportedDateSummaryDto> listRecentImportedDateSummaries() {
        return importOrderQueryService.listRecentDateSummaries();
    }

    /**
     * 删除指定日期已入库的单条订单，并返回该日最新列表
     */
    @Transactional
    public SplitResultResponse deleteImportedOrder(String systemNo, LocalDate date) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        LocalDate viewDate = importOrderQueryService.requireRecentDate(date);
        importOrderPersistenceService.deleteOrderForDate(systemNo, viewDate);
        return importOrderQueryService.listOrdersByDate(viewDate, null);
    }

    /**
     * 批量维护回单信息
     */
    @Transactional
    public BatchReceiptResponse batchUpdateReceipt(BatchReceiptRequest request, LocalDate date) {
        String content = request == null ? null : request.getContent();
        return importOrderReceiptService.batchUpdateReceipt(date, content);
    }

    /**
     * 手动修改单条订单商家，并返回该日最新列表
     */
    @Transactional
    public SplitResultResponse updateImportedOrderMerchant(
            String systemNo, LocalDate date, UpdateOrderMerchantRequest request) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        LocalDate viewDate = importOrderQueryService.requireRecentDate(date);
        String merchant = request == null ? null : request.getMerchant();
        importOrderPersistenceService.updateOrderMerchant(systemNo, viewDate, merchant);
        return importOrderQueryService.listOrdersByDate(viewDate, null);
    }

    /**
     * 手动修改单条订单可编辑字段，并返回该日最新列表
     */
    @Transactional
    public SplitResultResponse updateImportedOrderFields(
            String systemNo, LocalDate date, UpdateImportedOrderFieldsRequest request) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        LocalDate viewDate = importOrderQueryService.requireRecentDate(date);
        importOrderPersistenceService.updateOrderFields(systemNo, viewDate, request);
        return importOrderQueryService.listOrdersByDate(viewDate, null);
    }

    /**
     * 标记单条订单需售后，并返回该日最新列表
     */
    @Transactional
    public SplitResultResponse markImportedOrderAfterSales(
            String systemNo, LocalDate date, MarkAfterSalesRequest request) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        LocalDate viewDate = importOrderQueryService.requireRecentDate(date);
        String remark = request == null ? null : request.getRemark();
        importOrderPersistenceService.markOrderAfterSales(systemNo, viewDate, remark);
        return importOrderQueryService.listOrdersByDate(viewDate, null);
    }

    /**
     * 取消单条订单售后标记
     */
    @Transactional
    public void cancelImportedOrderAfterSales(String systemNo, LocalDate date) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        LocalDate viewDate = importOrderQueryService.requireRecentDate(date);
        importOrderPersistenceService.cancelOrderAfterSales(systemNo, viewDate);
    }

    /**
     * 标记单条订单售后完结
     */
    @Transactional
    public void completeImportedOrderAfterSales(String systemNo, LocalDate date) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        LocalDate viewDate = importOrderQueryService.requireRecentDate(date);
        importOrderPersistenceService.completeOrderAfterSales(systemNo, viewDate);
    }

    /**
     * 批量删除指定日期已入库订单，并返回该日最新列表
     */
    @Transactional
    public SplitResultResponse deleteImportedOrders(ExportSelectedRequest request, LocalDate date) {
        List<String> systemNos = request == null ? null : request.getSystemNos();
        if (systemNos == null || systemNos.isEmpty()) {
            throw new BusinessException("请先勾选要删除的订单");
        }
        LocalDate viewDate = importOrderQueryService.requireRecentDate(date);
        importOrderPersistenceService.deleteOrdersForDate(systemNos, viewDate);
        return importOrderQueryService.listOrdersByDate(viewDate, null);
    }

    /**
     * 上传导入：匹配平台并按商家关键字分单入库（不导出 Excel）
     */
    @Transactional
    public SplitResultResponse importByPlatform(MultipartFile file, String mappingJson) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        return splitByMerchant(file, mappingJson);
    }

    public AssignMerchantResult assignPendingMerchantsForRange(
            LocalDate startDate, LocalDate endDate) {
        return assignPendingMerchantsForRange(startDate, endDate, null, null);
    }

    /**
     * 按商家分单：已有商家保留不动，其余按关键字分单；导出可按平台/商家筛选
     */
    public AssignMerchantResult assignPendingMerchantsForRange(
            LocalDate startDate,
            LocalDate endDate,
            List<String> platforms,
            List<String> merchants) {
        LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
        LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
        if (normalizedStart.isAfter(normalizedEnd)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        long rangeSpanDays =
                java.time.temporal.ChronoUnit.DAYS.between(normalizedStart, normalizedEnd) + 1;
        if (rangeSpanDays > ImportOrderQueryService.MAX_IMPORT_RANGE_SPAN_DAYS) {
            throw new BusinessException(
                    "日期区间不能超过 " + ImportOrderQueryService.MAX_IMPORT_RANGE_SPAN_DAYS + " 天");
        }
        var persistenceResult =
                importOrderPersistenceService.assignPendingMerchantsInRange(
                        normalizedStart, normalizedEnd);
        LocalDate exportDate = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<ImportOrder> processedOrders = persistenceResult.processedOrders();
        List<ImportOrder> exportOrders =
                merchantSplitExportService.filterSplitExportOrders(
                        processedOrders, platforms, merchants);
        List<String> exportedSystemNos =
                exportOrders.stream().map(ImportOrder::getSystemNo).toList();
        ExportMode exportMode = exportSettingsService.getCurrentMode();
        List<String> exportedFiles = List.of();
        int exportedFileCount;
        try {
            PreparedSplitExport prepared =
                    merchantSplitExportService.prepareSplitExportFromOrders(
                            exportDate, exportOrders);
            exportedFileCount = prepared.entries().size();
            String exportDownloadToken = null;
            if (exportMode == ExportMode.SERVER_DIRECTORY) {
                exportedFiles =
                        merchantSplitExportService.writeSplitEntriesToDirectory(
                                prepared.exportDate(), prepared.entries());
            } else if (exportedFileCount > 0) {
                byte[] zipBytes =
                        merchantSplitExportService.buildSplitZipFromEntries(prepared.entries());
                exportDownloadToken =
                        exportDownloadCacheService.store(
                                "分单导出_" + exportDate + ".zip", zipBytes);
            }
            return new AssignMerchantResult(
                    persistenceResult.processedCount(),
                    persistenceResult.unmatchedPendingCount(),
                    exportDate.toString(),
                    exportedSystemNos,
                    exportDownloadToken,
                    exportedFiles,
                    exportedFileCount,
                    exportMode,
                    buildEmptySplitResult(normalizedEnd));
        } catch (IOException ex) {
            throw new BusinessException("导出 Excel 失败: " + ex.getMessage());
        }
    }

    /**
     * @deprecated 使用 {@link #assignPendingMerchantsForRange(LocalDate, LocalDate)}
     */
    @Deprecated
    public AssignMerchantResult assignAllPendingMerchants(LocalDate listViewDate) {
        LocalDate date =
                listViewDate == null
                        ? LocalDate.now(ZoneId.of("Asia/Shanghai"))
                        : importOrderQueryService.requireRecentDate(listViewDate);
        return assignPendingMerchantsForRange(date, date);
    }

    private SplitResultResponse buildEmptySplitResult(LocalDate exportDate) {
        return new SplitResultResponse(
                null, exportDate.toString(), 0, 0, 0, List.of(), List.of(), List.of(), 0);
    }

    @Deprecated
    @Transactional
    public AssignMerchantResult assignMerchantsForDate(LocalDate date) {
        return assignPendingMerchantsForRange(date, date);
    }

    /**
     * 按商家分单结果打包 ZIP（浏览器下载模式使用）
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportSplitByMerchant(
            LocalDate exportDate, List<String> systemNos, String downloadToken) {
        if (downloadToken != null && !downloadToken.isBlank()) {
            return buildCachedZipResponse(downloadToken);
        }
        LocalDate normalizedExportDate = importOrderQueryService.requireRecentDate(exportDate);
        try {
            byte[] zipBytes =
                    merchantSplitExportService.buildSplitExportZipBySystemNos(
                            normalizedExportDate, systemNos);
            String downloadName = "分单导出_" + normalizedExportDate + ".zip";
            return buildZipResponse(downloadName, zipBytes);
        } catch (IOException ex) {
            throw new BusinessException("导出 ZIP 失败: " + ex.getMessage());
        }
    }

    /**
     * 打开分单导出目录（testData/{exportDate}/分单/）
     */
    @Transactional(readOnly = true)
    public void openSplitExportDirectory(LocalDate exportDate) {
        if (exportSettingsService.getCurrentMode() != ExportMode.SERVER_DIRECTORY) {
            throw new BusinessException("当前导出方式不是桌面目录，无法自动打开文件夹");
        }
        LocalDate normalizedExportDate = importOrderQueryService.requireRecentDate(exportDate);
        folderOpenService.openDirectory(
                merchantSplitExportService.getSplitExportDirectory(normalizedExportDate));
    }

    /**
     * @deprecated 使用 {@link #openSplitExportDirectory(LocalDate)}
     */
    @Deprecated
    @Transactional(readOnly = true)
    public void openSplitExportDirectories(LocalDate startDate, LocalDate endDate) {
        openSplitExportDirectory(startDate);
    }

    /**
     * 打开平台模版导出目录（testData/{exportDate}/回单/）
     */
    @Transactional(readOnly = true)
    public void openReceiptExportDirectory(LocalDate exportDate) {
        if (exportSettingsService.getCurrentMode() != ExportMode.SERVER_DIRECTORY) {
            throw new BusinessException("当前导出方式不是桌面目录，无法自动打开文件夹");
        }
        LocalDate normalizedExportDate = importOrderQueryService.requireRecentDate(exportDate);
        folderOpenService.openDirectory(
                merchantSplitExportService.getReceiptExportDirectory(normalizedExportDate));
    }

    /**
     * @deprecated 使用 {@link #openReceiptExportDirectory(LocalDate)}
     */
    @Deprecated
    @Transactional(readOnly = true)
    public void openReceiptExportDirectories(LocalDate startDate, LocalDate endDate) {
        openReceiptExportDirectory(startDate);
    }

    /**
     * 按商家回单导出（根据系统配置写盘或仅返回文件数供浏览器下载）
     */
    public ReceiptExportResponse exportReceiptByMerchant(
            LocalDate startDate, LocalDate endDate, List<String> platforms) {
        ExportMode exportMode = exportSettingsService.getCurrentMode();
        try {
            PreparedReceiptExport prepared =
                    merchantSplitExportService.prepareReceiptExport(startDate, endDate, platforms);
            if (prepared.entries().isEmpty()) {
                throw buildReceiptExportEmptyException(startDate, endDate, platforms);
            }
            if (exportMode == ExportMode.SERVER_DIRECTORY) {
                List<String> exportedFiles =
                        merchantSplitExportService.writeReceiptEntriesToDirectory(prepared.entries());
                return new ReceiptExportResponse(
                        exportedFiles.size(),
                        exportedFiles,
                        exportMode,
                        null,
                        prepared.exportDate().toString());
            }
            LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
            LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
            String downloadName =
                    normalizedStart.equals(normalizedEnd)
                            ? "平台模版导出_" + normalizedStart + ".zip"
                            : "平台模版导出_" + normalizedStart + "_" + normalizedEnd + ".zip";
            byte[] zipBytes =
                    merchantSplitExportService.buildReceiptExportZipFromEntries(prepared.entries());
            String exportDownloadToken = exportDownloadCacheService.store(downloadName, zipBytes);
            return new ReceiptExportResponse(
                    prepared.entries().size(),
                    List.of(),
                    exportMode,
                    exportDownloadToken,
                    prepared.exportDate().toString());
        } catch (IOException ex) {
            throw new BusinessException("导出回单失败: " + ex.getMessage());
        }
    }

    private BusinessException buildReceiptExportEmptyException(
            LocalDate startDate, LocalDate endDate, List<String> platforms) {
        if (platforms != null && !platforms.isEmpty()) {
            return new BusinessException("所选平台没有可导出的已回单订单，请先填写物流信息");
        }
        long receiptedCount =
                merchantSplitExportService.countReceiptedOrdersInRange(startDate, endDate);
        if (receiptedCount == 0) {
            return new BusinessException("当前筛选时间内没有可导出的已回单订单，请先填写物流信息");
        }
        return new BusinessException(
                "当前筛选时间内有 "
                        + receiptedCount
                        + " 条已回单订单，但缺少平台模板配置，请检查系统配置中的表头映射");
    }

    /**
     * 按商家回单打包 ZIP（浏览器下载模式使用）
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportReceiptByMerchantZip(
            LocalDate startDate,
            LocalDate endDate,
            String downloadToken,
            List<String> platforms) {
        if (downloadToken != null && !downloadToken.isBlank()) {
            return buildCachedZipResponse(downloadToken);
        }
        try {
            byte[] zipBytes =
                    merchantSplitExportService.buildReceiptExportZip(startDate, endDate, platforms);
            LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
            LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
            String downloadName =
                    normalizedStart.equals(normalizedEnd)
                            ? "平台模版导出_" + normalizedStart + ".zip"
                            : "平台模版导出_" + normalizedStart + "_" + normalizedEnd + ".zip";
            return buildZipResponse(downloadName, zipBytes);
        } catch (IOException ex) {
            throw new BusinessException("导出回单 ZIP 失败: " + ex.getMessage());
        }
    }

    private ResponseEntity<Resource> buildCachedZipResponse(String downloadToken) {
        ExportDownloadCacheService.CachedExport cached =
                exportDownloadCacheService.take(downloadToken);
        return buildZipResponse(cached.fileName(), cached.zipBytes());
    }

    private ResponseEntity<Resource> buildZipResponse(String downloadName, byte[] zipBytes) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.attachment(downloadName))
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new ByteArrayResource(zipBytes));
    }

    /**
     * 按商家分单（兼容：若上传文件则整单导入+分单；推荐先 import 再 assign）
     */
    @Transactional
    public SplitResultResponse splitByMerchant(MultipartFile file, String mappingJson) {
        if (file == null || file.isEmpty()) {
            return assignMerchantsForDate(LocalDate.now(ZoneId.of("Asia/Shanghai"))).getOrders();
        }
        ProcessTask task = createTask(file.getOriginalFilename(), OperationType.SPLIT);
        task.setStatus(TaskStatus.PROCESSING);
        processTaskRepository.save(task);

        SplitExecution execution = executeSplit(file, mappingJson, task);
        completeTask(task, execution);

        return importOrderQueryService.listOrdersByDate(
                LocalDate.now(ZoneId.of("Asia/Shanghai")), task.getId());
    }

    /**
     * 导出选中的订单行（单个 Excel，非 ZIP）
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportSelectedOrders(ExportSelectedRequest request) {
        List<String> systemNos = request == null ? null : request.getSystemNos();
        if (systemNos == null || systemNos.isEmpty()) {
            throw new BusinessException("请先勾选要导出的订单");
        }
        Set<String> distinctSystemNos = new LinkedHashSet<>(systemNos);
        List<DailyTableRowDto> rows =
                importOrderQueryService.listRowsBySystemNos(new ArrayList<>(distinctSystemNos));
        if (rows.isEmpty()) {
            throw new BusinessException("未找到选中的订单数据，请刷新后重试");
        }
        if (rows.size() != distinctSystemNos.size()) {
            throw new BusinessException("部分选中订单不存在或已删除，请刷新后重选");
        }
        try {
            byte[] outputBytes = excelWriterService.writeDailyTable(rows);
            String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
            String downloadName = "选中发单表_" + today + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.attachment(downloadName))
                    .header("X-Output-Rows", String.valueOf(rows.size()))
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new ByteArrayResource(outputBytes));
        } catch (IOException ex) {
            throw new BusinessException("导出 Excel 失败: " + ex.getMessage());
        }
    }

    /**
     * 按平台模板导出选中订单（表头与列序与模板 Excel 一致，未映射列留空）
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportSelectedOrdersByPlatformTemplate(
            ExportSelectedRequest request) {
        List<String> systemNos = request == null ? null : request.getSystemNos();
        if (systemNos == null || systemNos.isEmpty()) {
            throw new BusinessException("请先勾选要导出的订单");
        }
        Set<String> distinctSystemNos = new LinkedHashSet<>(systemNos);
        List<DailyTableRowDto> rows =
                importOrderQueryService.listRowsBySystemNos(new ArrayList<>(distinctSystemNos));
        if (rows.isEmpty()) {
            throw new BusinessException("未找到选中的订单数据，请刷新后重试");
        }
        if (rows.size() != distinctSystemNos.size()) {
            throw new BusinessException("部分选中订单不存在或已删除，请刷新后重选");
        }

        Map<String, List<DailyTableRowDto>> rowsByPlatform = new LinkedHashMap<>();
        for (DailyTableRowDto row : rows) {
            String platform = resolveExportPlatformName(row.getPlatform());
            rowsByPlatform.computeIfAbsent(platform, key -> new ArrayList<>()).add(row);
        }

        List<ExcelWriterService.PlatformTemplateSheetExport> sheetExports = new ArrayList<>();
        for (Map.Entry<String, List<DailyTableRowDto>> entry : rowsByPlatform.entrySet()) {
            PlatformExportTemplateDto exportTemplate =
                    platformMappingTemplateService.resolveExportTemplate(entry.getKey());
            sheetExports.add(
                    new ExcelWriterService.PlatformTemplateSheetExport(
                            entry.getKey(),
                            entry.getValue(),
                            exportTemplate.getMapping(),
                            exportTemplate.getTemplateHeaders()));
        }

        try {
            byte[] outputBytes = excelWriterService.writePlatformTemplateWorkbook(sheetExports);
            String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
            String downloadName = "平台模版导出_" + today + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.attachment(downloadName))
                    .header("X-Output-Rows", String.valueOf(rows.size()))
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new ByteArrayResource(outputBytes));
        } catch (IOException ex) {
            throw new BusinessException("导出 Excel 失败: " + ex.getMessage());
        }
    }

    /**
     * 商家对账导出（不含平台/商家/供货价）
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportMerchantReconcile(ReconcileExportRequest request) {
        if (request == null) {
            throw new BusinessException("请选择日期区间与商家");
        }
        try {
            byte[] outputBytes =
                    reconcileExportService.exportMerchantReconcile(
                            request.getStartDate(), request.getEndDate(), request.getMerchant());
            String merchantLabel =
                    MerchantSplitExportService.sanitizeFileName(request.getMerchant(), "merchant");
            String downloadName =
                    buildReconcileDownloadName(
                            "商家对账", merchantLabel, request.getStartDate(), request.getEndDate());
            return buildExcelDownloadResponse(outputBytes, downloadName);
        } catch (IOException ex) {
            throw new BusinessException("导出 Excel 失败: " + ex.getMessage());
        }
    }

    /**
     * 平台对账导出（不含平台/商家/成本价）
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportPlatformReconcile(ReconcileExportRequest request) {
        if (request == null) {
            throw new BusinessException("请选择日期区间与平台");
        }
        try {
            byte[] outputBytes =
                    reconcileExportService.exportPlatformReconcile(
                            request.getStartDate(), request.getEndDate(), request.getPlatform());
            String platformLabel =
                    MerchantSplitExportService.sanitizeFileName(request.getPlatform(), "platform");
            String downloadName =
                    buildReconcileDownloadName(
                            "平台对账", platformLabel, request.getStartDate(), request.getEndDate());
            return buildExcelDownloadResponse(outputBytes, downloadName);
        } catch (IOException ex) {
            throw new BusinessException("导出 Excel 失败: " + ex.getMessage());
        }
    }

    /**
     * 售后订单导出（按售后状态排序：需售后在前，售后完结在后）
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportAfterSalesOrders(AfterSalesExportRequest request) {
        if (request == null) {
            throw new BusinessException("请选择日期区间");
        }
        try {
            byte[] outputBytes =
                    afterSalesExportService.exportAfterSalesOrders(
                            request.getStartDate(), request.getEndDate(), request.getKeyword());
            String rangeLabel =
                    request.getStartDate().equals(request.getEndDate())
                            ? request.getStartDate().toString()
                            : request.getStartDate() + "_" + request.getEndDate();
            String downloadName = "售后订单_" + rangeLabel + ".xlsx";
            return buildExcelDownloadResponse(outputBytes, downloadName);
        } catch (IOException ex) {
            throw new BusinessException("导出 Excel 失败: " + ex.getMessage());
        }
    }

    private String buildReconcileDownloadName(
            String prefix, String targetLabel, LocalDate startDate, LocalDate endDate) {
        String rangeLabel =
                startDate.equals(endDate)
                        ? startDate.toString()
                        : startDate + "_" + endDate;
        return prefix + "_" + targetLabel + "_" + rangeLabel + ".xlsx";
    }

    private ResponseEntity<Resource> buildExcelDownloadResponse(byte[] outputBytes, String downloadName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.attachment(downloadName))
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(outputBytes));
    }

    private String resolveExportPlatformName(String platform) {
        if (platform == null || platform.isBlank()) {
            return ImportOrderQueryService.UNKNOWN_PLATFORM;
        }
        return platform.trim();
    }

    private SplitExecution executeSplit(
            MultipartFile file, String mappingJson, ProcessTask task) {
        ParsedImportExcel parsed =
                excelParserService.parseImportOnce(
                        file, platformMappingTemplateService::matchByHeaders, false);
        List<OrderRow> inputRows = parsed.rows();
        int pendingMerchantRowCount = 0;
        for (OrderRow row : inputRows) {
            String merchant = merchantConfigService.resolveByProductName(row.getProductName());
            if (MerchantConfigService.UNMATCHED_MERCHANT_NAME.equals(merchant)) {
                row.setMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
                pendingMerchantRowCount++;
            } else {
                row.setMerchant(merchant);
            }
        }

        Map<String, List<OrderRow>> splitResult = orderSplitMergeService.groupByMerchant(inputRows);

        LocalDateTime issueDateTime = dailyTableService.currentIssueDateTime();
        int savedCount =
                importOrderPersistenceService.saveSplitOrders(
                        task.getId(), parsed.platform(), splitResult, issueDateTime);

        String issueDateText = dailyTableService.formatIssueDate(issueDateTime);
        return new SplitExecution(
                inputRows.size(), savedCount, splitResult.size(), issueDateText, pendingMerchantRowCount);
    }

    private void completeTask(ProcessTask task, SplitExecution execution) {
        task.setInputRowCount(execution.inputRowCount());
        task.setMerchantGroupCount(execution.batchMerchantCount());
        task.setOutputRowCount(execution.batchRowCount());
        task.setStatus(TaskStatus.SUCCESS);
        String message =
                "本次导入 "
                        + execution.batchRowCount()
                        + " 行已追加入库（当日累计请查看列表），发单时间 "
                        + execution.issueDateText();
        if (execution.pendingMerchantRowCount() > 0) {
            message +=
                    "，其中 "
                            + execution.pendingMerchantRowCount()
                            + " 行未匹配商家关键字，已归入未定义";
        }
        task.setMessage(message);
        processTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId) {
        ProcessTask task = processTaskRepository
                .findById(taskId)
                .orElseThrow(() -> new BusinessException("任务不存在: " + taskId));
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listRecentTasks() {
        return processTaskRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private ProcessTask createTask(String fileName, OperationType operationType) {
        ProcessTask task = new ProcessTask();
        task.setOriginalFileName(fileName == null ? "unknown.xlsx" : fileName);
        task.setOperationType(operationType);
        task.setStatus(TaskStatus.PENDING);
        return task;
    }

    private TaskResponse toResponse(ProcessTask task) {
        return TaskResponse.builder()
                .taskId(task.getId())
                .originalFileName(task.getOriginalFileName())
                .operationType(task.getOperationType())
                .status(task.getStatus())
                .message(task.getMessage())
                .inputRowCount(task.getInputRowCount())
                .merchantGroupCount(task.getMerchantGroupCount())
                .outputRowCount(task.getOutputRowCount())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private record SplitExecution(
            int inputRowCount,
            int batchRowCount,
            int batchMerchantCount,
            String issueDateText,
            int pendingMerchantRowCount) {
    }

}
