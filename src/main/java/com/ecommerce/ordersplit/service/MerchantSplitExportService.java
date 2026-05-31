package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.PlatformExportTemplateDto;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 分单后按商家导出 Excel
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class MerchantSplitExportService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FOLDER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String EXPORT_ROOT = "testData";
    private static final String SPLIT_SUBDIR = "分单";
    private static final String RECEIPT_SUBDIR = "回单";
    private static final String RECONCILE_SUBDIR = "对账";

    private static final String GROUP_KEY_SEPARATOR = "\u0001";

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderQueryService importOrderQueryService;
    private final ExcelWriterService excelWriterService;
    private final PlatformMappingTemplateService platformMappingTemplateService;

    /**
     * 将本次分单涉及的订单按商家导出到指定操作日目录（testData/{exportDate}/分单）
     */
    public List<String> exportSplitOrdersForExportDate(
            LocalDate exportDate, List<ImportOrder> orders) throws IOException {
        List<MerchantExportEntry> entries = buildSplitExportEntriesForOrders(exportDate, orders);
        return writeSplitEntriesToDirectory(exportDate, entries);
    }

    /**
     * 统计本次分单订单按商家导出的 Excel 数量
     */
    public int countSplitExportForOrders(LocalDate exportDate, List<ImportOrder> orders) {
        return buildSplitExportEntriesForOrders(exportDate, orders).size();
    }

    /**
     * 将本次分单涉及的订单按商家打包为 ZIP（浏览器下载）
     */
    public byte[] buildSplitExportZipForOrders(LocalDate exportDate, List<ImportOrder> orders)
            throws IOException {
        List<MerchantExportEntry> entries = buildSplitExportEntriesForOrders(exportDate, orders);
        return buildSplitZip(entries);
    }

    /**
     * 按系统编号加载订单并导出到指定操作日目录
     */
    public List<String> exportSplitOrdersBySystemNos(LocalDate exportDate, List<String> systemNos)
            throws IOException {
        PreparedSplitExport prepared = prepareSplitExportBySystemNos(exportDate, systemNos);
        return writeSplitEntriesToDirectory(prepared.exportDate(), prepared.entries());
    }

    /**
     * 单次构建回单导出入口，供写盘/ZIP/计数复用
     */
    public PreparedReceiptExport prepareReceiptExport(LocalDate startDate, LocalDate endDate) {
        return prepareReceiptExport(startDate, endDate, null);
    }

    public PreparedReceiptExport prepareReceiptExport(
            LocalDate startDate, LocalDate endDate, List<String> platforms) {
        LocalDate exportDate = LocalDate.now(ZONE_SHANGHAI);
        List<MerchantExportEntry> entries =
                buildReceiptExportEntries(startDate, endDate, exportDate, platforms);
        return new PreparedReceiptExport(startDate, endDate, exportDate, entries);
    }

    public int countSplitExportBySystemNos(LocalDate exportDate, List<String> systemNos) {
        return prepareSplitExportBySystemNos(exportDate, systemNos).entries().size();
    }

    public byte[] buildSplitExportZipBySystemNos(LocalDate exportDate, List<String> systemNos)
            throws IOException {
        PreparedSplitExport prepared = prepareSplitExportBySystemNos(exportDate, systemNos);
        return buildSplitZip(prepared.entries());
    }

    /**
     * 直接使用已分单订单分组（避免二次查库）
     */
    public PreparedSplitExport prepareSplitExportFromOrders(
            LocalDate exportDate, List<ImportOrder> orders) {
        return prepareSplitExportFromOrders(exportDate, orders, null);
    }

    public PreparedSplitExport prepareSplitExportFromOrders(
            LocalDate exportDate, List<ImportOrder> orders, String exportBatchSuffix) {
        List<MerchantExportEntry> entries =
                buildSplitExportEntriesForOrders(exportDate, orders, exportBatchSuffix);
        LocalDate normalized = importOrderQueryService.requireRecentDate(exportDate);
        return new PreparedSplitExport(normalized, entries);
    }

    /**
     * 按平台/商家筛选参与分单导出的订单（空列表表示不过滤）
     */
    public List<ImportOrder> filterSplitExportOrders(
            List<ImportOrder> orders, List<String> platforms, List<String> merchants) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        Set<String> platformFilter = normalizePlatformFilter(platforms);
        Set<String> merchantFilter = normalizeMerchantFilter(merchants);
        if (platformFilter == null && merchantFilter == null) {
            return new ArrayList<>(orders);
        }
        List<ImportOrder> filtered = new ArrayList<>();
        for (ImportOrder order : orders) {
            if (platformFilter != null
                    && !platformFilter.contains(resolvePlatformName(order.getPlatform()))) {
                continue;
            }
            if (merchantFilter != null && !matchesMerchantFilter(order, merchantFilter)) {
                continue;
            }
            filtered.add(order);
        }
        return filtered;
    }

    private boolean matchesMerchantFilter(ImportOrder order, Set<String> merchantFilter) {
        String merchant = order.getMerchant();
        if (merchant == null || merchant.isBlank()) {
            return false;
        }
        return merchantFilter.contains(merchant.trim());
    }

    private Set<String> normalizeMerchantFilter(List<String> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String merchant : merchants) {
            if (merchant == null || merchant.isBlank()) {
                continue;
            }
            normalized.add(merchant.trim());
        }
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 单次查库并分组，供写盘/ZIP/计数复用
     */
    public PreparedSplitExport prepareSplitExportBySystemNos(
            LocalDate exportDate, List<String> systemNos) {
        List<ImportOrder> orders = loadOrdersForSplitExport(systemNos);
        List<MerchantExportEntry> entries = buildSplitExportEntriesForOrders(exportDate, orders);
        return new PreparedSplitExport(exportDate, entries);
    }

    public byte[] buildSplitZipFromEntries(List<MerchantExportEntry> entries) throws IOException {
        return buildSplitZip(entries);
    }

    private List<ImportOrder> loadOrdersForSplitExport(List<String> systemNos) {
        if (systemNos == null || systemNos.isEmpty()) {
            throw new BusinessException("没有可导出的分单订单");
        }
        List<ImportOrder> orders =
                importOrderRepository.findBySystemNoInOrderByMerchantAscSystemNoAsc(systemNos);
        if (orders.isEmpty()) {
            throw new BusinessException("没有可导出的分单订单");
        }
        return orders;
    }

    /**
     * 将指定日期已分单订单按商家导出到桌面 testData/{日期}/分单 目录
     */
    public List<String> exportByMerchantForDate(LocalDate date) throws IOException {
        List<MerchantExportEntry> entries = buildSplitExportEntries(date);
        return writeSplitEntriesToDirectory(date, entries);
    }

    /**
     * 将指定日期已分单订单按商家打包为 ZIP（浏览器下载）
     */
    public byte[] buildMerchantExportZip(LocalDate date) throws IOException {
        List<MerchantExportEntry> entries = buildSplitExportEntries(date);
        return buildSplitZip(entries);
    }

    /**
     * 统计指定日期可导出的商家 Excel 数量
     */
    public int countMerchantExports(LocalDate date) {
        return buildSplitExportEntries(date).size();
    }

    /**
     * 将多个分单日期的已分单订单按商家导出到桌面 testData/{日期}/分单 目录
     */
    public List<String> exportByMerchantForDates(Iterable<LocalDate> dates) throws IOException {
        List<String> exportedFiles = new ArrayList<>();
        for (LocalDate date : dates) {
            exportedFiles.addAll(exportByMerchantForDate(date));
        }
        return exportedFiles;
    }

    /**
     * 将多个分单日期已分单订单打包为 ZIP（浏览器下载）
     */
    public byte[] buildMerchantExportZipForDates(Iterable<LocalDate> dates) throws IOException {
        List<MerchantExportEntry> entries = new ArrayList<>();
        for (LocalDate date : dates) {
            entries.addAll(buildSplitExportEntries(date));
        }
        return buildSplitZip(entries);
    }

    /**
     * 统计多个分单日期可导出的商家 Excel 数量
     */
    public int countMerchantExportsForDates(Iterable<LocalDate> dates) {
        int total = 0;
        for (LocalDate date : dates) {
            total += countMerchantExports(date);
        }
        return total;
    }

    public int countReceiptExports(LocalDate startDate, LocalDate endDate) {
        return prepareReceiptExport(startDate, endDate).entries().size();
    }

    /**
     * 将日期区间内已回单订单按商家导出到桌面 testData/{日期}/回单 目录
     */
    public List<String> exportReceiptByMerchantForRange(LocalDate startDate, LocalDate endDate)
            throws IOException {
        PreparedReceiptExport prepared = prepareReceiptExport(startDate, endDate);
        if (prepared.entries().isEmpty()) {
            return List.of();
        }
        return writeReceiptEntriesToDirectory(prepared.entries());
    }

    /**
     * 将日期区间内已回单订单按商家打包为 ZIP（浏览器下载）
     */
    public byte[] buildReceiptExportZip(LocalDate startDate, LocalDate endDate) throws IOException {
        return buildReceiptExportZip(startDate, endDate, null);
    }

    public byte[] buildReceiptExportZip(
            LocalDate startDate, LocalDate endDate, List<String> platforms) throws IOException {
        PreparedReceiptExport prepared = prepareReceiptExport(startDate, endDate, platforms);
        if (prepared.entries().isEmpty()) {
            throw new BusinessException("当前筛选时间内没有可导出的已回单订单，请先填写物流信息");
        }
        return buildReceiptZip(prepared.entries());
    }

    public byte[] buildReceiptExportZipFromEntries(List<MerchantExportEntry> entries)
            throws IOException {
        if (entries.isEmpty()) {
            throw new BusinessException("当前筛选时间内没有可导出的已回单订单，请先填写物流信息");
        }
        return buildReceiptZip(entries);
    }

    List<MerchantExportEntry> buildSplitExportEntries(LocalDate date) {
        return buildExportEntries(
                date,
                order -> true,
                ExportKind.SPLIT);
    }

    List<MerchantExportEntry> buildSplitExportEntriesForOrders(
            LocalDate exportDate, List<ImportOrder> orders) {
        return buildSplitExportEntriesForOrders(exportDate, orders, null);
    }

    List<MerchantExportEntry> buildSplitExportEntriesForOrders(
            LocalDate exportDate, List<ImportOrder> orders, String exportBatchSuffix) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        LocalDate normalized = importOrderQueryService.requireRecentDate(exportDate);
        List<ImportOrder> sortedOrders = new ArrayList<>(orders);
        sortedOrders.sort(
                (left, right) -> {
                    int platformCompare =
                            resolvePlatformName(left.getPlatform())
                                    .compareTo(resolvePlatformName(right.getPlatform()));
                    if (platformCompare != 0) {
                        return platformCompare;
                    }
                    String leftMerchant = left.getMerchant() == null ? "" : left.getMerchant();
                    String rightMerchant = right.getMerchant() == null ? "" : right.getMerchant();
                    int merchantCompare = leftMerchant.compareTo(rightMerchant);
                    if (merchantCompare != 0) {
                        return merchantCompare;
                    }
                    String leftSystemNo = left.getSystemNo() == null ? "" : left.getSystemNo();
                    String rightSystemNo = right.getSystemNo() == null ? "" : right.getSystemNo();
                    return leftSystemNo.compareTo(rightSystemNo);
                });

        Map<String, MerchantExportGroup> groups = new LinkedHashMap<>();
        for (ImportOrder order : sortedOrders) {
            String merchant = order.getMerchant();
            if (!isExportableMerchant(merchant, ExportKind.SPLIT.requireAssignedMerchant)) {
                continue;
            }
            groups.computeIfAbsent(
                            merchant,
                            key -> new MerchantExportGroup(merchant, null, new ArrayList<>()))
                    .rows()
                    .add(importOrderQueryService.toRowDto(order));
        }
        return buildSplitExportEntriesFromGroups(
                normalized, groups, ExportKind.SPLIT, exportBatchSuffix);
    }

    private List<MerchantExportEntry> buildSplitExportEntriesFromGroups(
            LocalDate exportDate,
            Map<String, MerchantExportGroup> groups,
            ExportKind exportKind) {
        return buildSplitExportEntriesFromGroups(exportDate, groups, exportKind, null);
    }

    private List<MerchantExportEntry> buildSplitExportEntriesFromGroups(
            LocalDate exportDate,
            Map<String, MerchantExportGroup> groups,
            ExportKind exportKind,
            String exportBatchSuffix) {
        String dateLabel = exportDate.format(DATE_FOLDER);
        List<MerchantExportEntry> entries = new ArrayList<>();
        Set<String> usedFileNames = new HashSet<>();
        for (MerchantExportGroup group : groups.values()) {
            List<DailyTableRowDto> rows = group.rows();
            if (rows.isEmpty()) {
                continue;
            }
            String merchant = group.merchant();
            String platform = group.platform();
            String batchSuffix = exportKind == ExportKind.SPLIT ? exportBatchSuffix : null;
            String fileName = buildMerchantDateFileName(merchant, exportDate, batchSuffix);
            if (!usedFileNames.add(fileName)) {
                fileName = buildMerchantPlatformDateFileName(merchant, platform, exportDate, batchSuffix);
                usedFileNames.add(fileName);
            }
            String zipEntryPath = exportDate + "/" + exportKind.subDir + "/" + fileName;
            String sheetTitle = merchant + dateLabel + exportKind.sheetTitleSuffix;
            entries.add(
                    new MerchantExportEntry(
                            exportDate,
                            fileName,
                            zipEntryPath,
                            sheetTitle,
                            rows,
                            null));
        }
        return entries;
    }

    List<MerchantExportEntry> buildReceiptExportEntries(
            LocalDate startDate, LocalDate endDate, LocalDate exportDate) {
        return buildReceiptExportEntries(startDate, endDate, exportDate, null);
    }

    List<MerchantExportEntry> buildReceiptExportEntries(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate exportDate,
            List<String> platforms) {
        LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
        LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
        if (normalizedStart.isAfter(normalizedEnd)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        long rangeSpanDays = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd) + 1;
        if (rangeSpanDays > ImportOrderQueryService.MAX_IMPORT_RANGE_SPAN_DAYS) {
            throw new BusinessException(
                    "日期区间不能超过 " + ImportOrderQueryService.MAX_IMPORT_RANGE_SPAN_DAYS + " 天");
        }

        LocalDate normalizedExportDate = importOrderQueryService.requireRecentDate(exportDate);
        Set<String> platformFilter = normalizePlatformFilter(platforms);
        LocalDateTime rangeStart = normalizedStart.atStartOfDay();
        LocalDateTime rangeEndExclusive = normalizedEnd.plusDays(1).atStartOfDay();
        List<ImportOrder> orders =
                importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                rangeStart, rangeEndExclusive);

        Map<String, PlatformExportTemplateDto> templateCache = new HashMap<>();
        Map<String, PlatformReceiptExportGroup> groups = new LinkedHashMap<>();
        for (ImportOrder order : orders) {
            if (order.getReceiptStatus() != ImportOrderReceiptStatus.RECEIPTED) {
                continue;
            }
            if (order.getIssueDate() == null) {
                continue;
            }
            String platform = resolvePlatformName(order.getPlatform());
            if (platformFilter != null && !platformFilter.contains(platform)) {
                continue;
            }
            groups.computeIfAbsent(
                            platform,
                            key -> new PlatformReceiptExportGroup(platform, new ArrayList<>()))
                    .rows()
                    .add(importOrderQueryService.toRowDto(order));
        }

        List<MerchantExportEntry> entries = new ArrayList<>();
        for (PlatformReceiptExportGroup group : groups.values()) {
            entries.add(buildPlatformReceiptEntry(group, normalizedExportDate, templateCache));
        }
        return entries;
    }

    private MerchantExportEntry buildPlatformReceiptEntry(
            PlatformReceiptExportGroup group,
            LocalDate exportDate,
            Map<String, PlatformExportTemplateDto> templateCache) {
        List<DailyTableRowDto> rows = group.rows();
        String platform = group.platform();
        String fileName = buildPlatformDateFileName(platform, exportDate);
        String zipEntryPath = exportDate + "/" + ExportKind.RECEIPT.subDir + "/" + fileName;
        String dateLabel = exportDate.format(DATE_FOLDER);
        String sheetTitle = platform + dateLabel + ExportKind.RECEIPT.sheetTitleSuffix;
        PlatformExportTemplateDto exportTemplate =
                templateCache.computeIfAbsent(
                        platform, platformMappingTemplateService::resolveExportTemplate);
        return new MerchantExportEntry(
                exportDate, fileName, zipEntryPath, sheetTitle, rows, exportTemplate);
    }

    /**
     * 统计日期区间内已回单订单数（用于导出失败提示）
     */
    long countReceiptedOrdersInRange(LocalDate startDate, LocalDate endDate) {
        LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
        LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
        if (normalizedStart.isAfter(normalizedEnd)) {
            return 0;
        }
        long total = 0;
        for (LocalDate day = normalizedStart;
                !day.isAfter(normalizedEnd);
                day = day.plusDays(1)) {
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            total +=
                    importOrderRepository
                            .countByIssueDateGreaterThanEqualAndIssueDateLessThanAndReceiptStatus(
                                    start, end, ImportOrderReceiptStatus.RECEIPTED);
        }
        return total;
    }

    private List<MerchantExportEntry> buildExportEntries(
            LocalDate date,
            Predicate<ImportOrder> orderFilter,
            ExportKind exportKind) {
        LocalDate normalized = importOrderQueryService.requireRecentDate(date);
        LocalDateTime start = normalized.atStartOfDay();
        LocalDateTime end = normalized.plusDays(1).atStartOfDay();
        List<ImportOrder> orders =
                importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                start, end);

        Map<String, MerchantExportGroup> groups = new LinkedHashMap<>();
        for (ImportOrder order : orders) {
            if (!orderFilter.test(order)) {
                continue;
            }
            String merchant = order.getMerchant();
            if (!isExportableMerchant(merchant, exportKind.requireAssignedMerchant)) {
                continue;
            }
            String platform = resolvePlatformName(order.getPlatform());
            String groupKey =
                    exportKind.requirePlatformTemplate
                            ? merchant + GROUP_KEY_SEPARATOR + platform
                            : merchant;
            groups.computeIfAbsent(
                            groupKey,
                            key ->
                                    new MerchantExportGroup(
                                            merchant,
                                            exportKind.requirePlatformTemplate ? platform : null,
                                            new ArrayList<>()))
                    .rows()
                    .add(importOrderQueryService.toRowDto(order));
        }

        String dateLabel = normalized.format(DATE_FOLDER);
        List<MerchantExportEntry> entries = new ArrayList<>();
        Set<String> usedFileNames = new HashSet<>();
        Map<String, PlatformExportTemplateDto> templateCache = new HashMap<>();
        for (MerchantExportGroup group : groups.values()) {
            List<DailyTableRowDto> rows = group.rows();
            if (rows.isEmpty()) {
                continue;
            }
            String merchant = group.merchant();
            String platform = group.platform();
            String fileName = buildMerchantDateFileName(merchant, normalized);
            if (!usedFileNames.add(fileName)) {
                fileName = buildMerchantPlatformDateFileName(merchant, platform, normalized);
                usedFileNames.add(fileName);
            }
            String zipEntryPath =
                    normalized + "/" + exportKind.subDir + "/" + fileName;
            String sheetTitle = merchant + dateLabel + exportKind.sheetTitleSuffix;
            PlatformExportTemplateDto exportTemplate = null;
            if (exportKind.requirePlatformTemplate) {
                exportTemplate =
                        templateCache.computeIfAbsent(
                                platform, platformMappingTemplateService::resolveExportTemplate);
            }
            entries.add(
                    new MerchantExportEntry(
                            normalized,
                            fileName,
                            zipEntryPath,
                            sheetTitle,
                            rows,
                            exportTemplate));
        }
        return entries;
    }

    static String buildPlatformDateFileName(String platform, LocalDate date) {
        return sanitizeFileName(platform, "platform") + "-" + date.format(DATE_FOLDER) + ".xlsx";
    }

    static String buildMerchantDateFileName(String merchant, LocalDate date) {
        return buildMerchantDateFileName(merchant, date, null);
    }

    static String buildMerchantDateFileName(String merchant, LocalDate date, String batchSuffix) {
        String base = sanitizeFileName(merchant, "merchant") + "-" + date.format(DATE_FOLDER);
        if (batchSuffix != null && !batchSuffix.isBlank()) {
            base = base + "-" + batchSuffix.trim();
        }
        return base + ".xlsx";
    }

    static String buildMerchantPlatformDateFileName(
            String merchant, String platform, LocalDate date) {
        return buildMerchantPlatformDateFileName(merchant, platform, date, null);
    }

    static String buildMerchantPlatformDateFileName(
            String merchant, String platform, LocalDate date, String batchSuffix) {
        String base =
                sanitizeFileName(merchant, "merchant")
                        + "-"
                        + sanitizeFileName(platform, "platform")
                        + "-"
                        + date.format(DATE_FOLDER);
        if (batchSuffix != null && !batchSuffix.isBlank()) {
            base = base + "-" + batchSuffix.trim();
        }
        return base + ".xlsx";
    }

    static String sanitizeFileName(String name) {
        return sanitizeFileName(name, "merchant");
    }

    static String sanitizeFileName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String sanitized = name.trim().replaceAll("[\\\\/*?\\[\\]:]", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private boolean isExportableMerchant(String merchant, boolean requireAssignedMerchant) {
        if (merchant == null || merchant.isBlank()) {
            return false;
        }
        if (MerchantConfigService.UNMATCHED_MERCHANT_NAME.equals(merchant)) {
            return false;
        }
        if (!requireAssignedMerchant) {
            return true;
        }
        return !MerchantConfigService.PENDING_SPLIT_MERCHANT.equals(merchant);
    }

    private String resolvePlatformName(String platform) {
        if (platform == null || platform.isBlank()) {
            return ImportOrderQueryService.UNKNOWN_PLATFORM;
        }
        return platform.trim();
    }

    private Set<String> normalizePlatformFilter(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String platform : platforms) {
            if (platform == null || platform.isBlank()) {
                continue;
            }
            normalized.add(resolvePlatformName(platform));
        }
        return normalized.isEmpty() ? null : normalized;
    }

    public List<String> writeSplitEntriesToDirectory(
            LocalDate date, List<MerchantExportEntry> entries) throws IOException {
        if (entries.isEmpty()) {
            return List.of();
        }
        ensureDateExportLayout(date);
        Path splitDir = resolveSplitExportDir(date);
        try {
            return writePreparedFilesToDirectory(
                    entries.parallelStream()
                            .map(
                                    entry -> {
                                        try {
                                            byte[] fileBytes =
                                                    excelWriterService.writeMerchantDailyTable(
                                                            entry.sheetTitle(), entry.rows());
                                            return new PreparedDiskFile(
                                                    resolveUniqueOutputPath(
                                                            splitDir, entry.fileName()),
                                                    fileBytes);
                                        } catch (IOException ex) {
                                            throw new ExportIOException(ex);
                                        }
                                    })
                            .toList());
        } catch (ExportIOException ex) {
            throw ex.unwrap();
        }
    }

    public List<String> writeReceiptEntriesToDirectory(List<MerchantExportEntry> entries)
            throws IOException {
        if (entries.isEmpty()) {
            return List.of();
        }
        Set<LocalDate> exportDates = new HashSet<>();
        for (MerchantExportEntry entry : entries) {
            exportDates.add(entry.exportDate());
        }
        for (LocalDate exportDate : exportDates) {
            ensureDateExportLayout(exportDate);
        }
        try {
            return writePreparedFilesToDirectory(
                    entries.parallelStream()
                            .map(
                                    entry -> {
                                        try {
                                            byte[] fileBytes =
                                                    excelWriterService.writeMerchantReceiptTable(
                                                            entry.exportTemplate().getPlatform(),
                                                            entry.sheetTitle(),
                                                            entry.rows(),
                                                            entry.exportTemplate().getMapping(),
                                                            entry.exportTemplate().getTemplateHeaders());
                                            Path outputPath =
                                                    resolveUniqueOutputPath(
                                                            resolveReceiptExportDir(
                                                                    entry.exportDate()),
                                                            entry.fileName());
                                            return new PreparedDiskFile(outputPath, fileBytes);
                                        } catch (IOException ex) {
                                            throw new ExportIOException(ex);
                                        }
                                    })
                            .toList());
        } catch (ExportIOException ex) {
            throw ex.unwrap();
        }
    }

    private Path resolveUniqueOutputPath(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }
        for (int sequence = 2; sequence < 1000; sequence++) {
            candidate = directory.resolve(baseName + "(" + sequence + ")" + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("无法生成唯一导出文件名: " + fileName);
    }

    private List<String> writePreparedFilesToDirectory(List<PreparedDiskFile> preparedFiles)
            throws IOException {
        List<String> exportedFiles = new ArrayList<>(preparedFiles.size());
        for (PreparedDiskFile preparedFile : preparedFiles) {
            Files.write(
                    preparedFile.outputPath(),
                    preparedFile.fileBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            exportedFiles.add(preparedFile.outputPath().toAbsolutePath().toString());
        }
        return exportedFiles;
    }

    private byte[] buildSplitZip(List<MerchantExportEntry> entries) throws IOException {
        if (entries.isEmpty()) {
            throw new BusinessException("当前日期没有可导出的商家订单");
        }
        return buildZipFromPreparedFiles(generateSplitExcelFiles(entries));
    }

    private byte[] buildReceiptZip(List<MerchantExportEntry> entries) throws IOException {
        return buildZipFromPreparedFiles(generateReceiptExcelFiles(entries));
    }

    private List<PreparedZipFile> generateSplitExcelFiles(List<MerchantExportEntry> entries) {
        return entries.parallelStream().map(this::generateSplitExcelFile).toList();
    }

    private PreparedZipFile generateSplitExcelFile(MerchantExportEntry entry) {
        try {
            byte[] fileBytes =
                    excelWriterService.writeMerchantDailyTable(entry.sheetTitle(), entry.rows());
            return new PreparedZipFile(entry.zipEntryPath(), fileBytes);
        } catch (IOException ex) {
            throw new ExportIOException(ex);
        }
    }

    private List<PreparedZipFile> generateReceiptExcelFiles(List<MerchantExportEntry> entries) {
        return entries.parallelStream().map(this::generateReceiptExcelFile).toList();
    }

    private PreparedZipFile generateReceiptExcelFile(MerchantExportEntry entry) {
        try {
            byte[] fileBytes =
                    excelWriterService.writeMerchantReceiptTable(
                            entry.exportTemplate().getPlatform(),
                            entry.sheetTitle(),
                            entry.rows(),
                            entry.exportTemplate().getMapping(),
                            entry.exportTemplate().getTemplateHeaders());
            return new PreparedZipFile(entry.zipEntryPath(), fileBytes);
        } catch (IOException ex) {
            throw new ExportIOException(ex);
        }
    }

    private byte[] buildZipFromPreparedFiles(List<PreparedZipFile> preparedFiles) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.setLevel(Deflater.BEST_SPEED);
            for (PreparedZipFile preparedFile : preparedFiles) {
                ZipEntry zipEntry = new ZipEntry(preparedFile.zipEntryPath());
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(preparedFile.fileBytes());
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    /**
     * 指定日期的分单导出目录（桌面 testData/{日期}/分单/）
     */
    public Path getSplitExportDirectory(LocalDate date) {
        return resolveSplitExportDir(date);
    }

    /**
     * 指定日期的回单导出目录（桌面 testData/{日期}/回单/）
     */
    public Path getReceiptExportDirectory(LocalDate date) {
        return resolveReceiptExportDir(date);
    }

    /**
     * @deprecated 使用 {@link #getSplitExportDirectory(LocalDate)}
     */
    @Deprecated
    public Path getDateExportDirectory(LocalDate date) {
        return getSplitExportDirectory(date);
    }

    Path resolveDateRootDir(LocalDate date) {
        Path desktop = Paths.get(System.getProperty("user.home"), "Desktop");
        if (!Files.isDirectory(desktop)) {
            throw new BusinessException("未找到桌面目录，无法导出 Excel");
        }
        return desktop.resolve(EXPORT_ROOT).resolve(date.format(DATE_FOLDER));
    }

    Path resolveSplitExportDir(LocalDate date) {
        return resolveDateRootDir(date).resolve(SPLIT_SUBDIR);
    }

    Path resolveReceiptExportDir(LocalDate date) {
        return resolveDateRootDir(date).resolve(RECEIPT_SUBDIR);
    }

    void ensureDateExportLayout(LocalDate date) throws IOException {
        Files.createDirectories(resolveSplitExportDir(date));
        Files.createDirectories(resolveReceiptExportDir(date));
        Files.createDirectories(resolveDateRootDir(date).resolve(RECONCILE_SUBDIR));
    }

    private enum ExportKind {
        SPLIT(SPLIT_SUBDIR, "", false, false),
        RECEIPT(RECEIPT_SUBDIR, "-回单", true, false);

        private final String subDir;
        private final String sheetTitleSuffix;
        private final boolean requirePlatformTemplate;
        private final boolean requireAssignedMerchant;

        ExportKind(
                String subDir,
                String sheetTitleSuffix,
                boolean requirePlatformTemplate,
                boolean requireAssignedMerchant) {
            this.subDir = subDir;
            this.sheetTitleSuffix = sheetTitleSuffix;
            this.requirePlatformTemplate = requirePlatformTemplate;
            this.requireAssignedMerchant = requireAssignedMerchant;
        }
    }

    private record MerchantExportGroup(String merchant, String platform, List<DailyTableRowDto> rows) {}

    private record PlatformReceiptExportGroup(String platform, List<DailyTableRowDto> rows) {}

    public record PreparedSplitExport(
            LocalDate exportDate, List<MerchantExportEntry> entries) {}

    public record PreparedReceiptExport(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate exportDate,
            List<MerchantExportEntry> entries) {}

    private record PreparedZipFile(String zipEntryPath, byte[] fileBytes) {}

    private record PreparedDiskFile(Path outputPath, byte[] fileBytes) {}

    private static final class ExportIOException extends RuntimeException {
        ExportIOException(IOException cause) {
            super(cause);
        }

        IOException unwrap() {
            return (IOException) getCause();
        }
    }

    record MerchantExportEntry(
            LocalDate exportDate,
            String fileName,
            String zipEntryPath,
            String sheetTitle,
            List<DailyTableRowDto> rows,
            PlatformExportTemplateDto exportTemplate) {}
}
