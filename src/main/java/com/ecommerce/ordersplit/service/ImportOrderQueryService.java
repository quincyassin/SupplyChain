package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.ImportedDateSummaryDto;
import com.ecommerce.ordersplit.dto.MerchantSplitGroupDto;
import com.ecommerce.ordersplit.dto.PlatformSummaryDto;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 已入库订单查询（按分单日期、平台、商家分组）
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ImportOrderQueryService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 历史数据无平台字段时的展示名 */
    public static final String UNKNOWN_PLATFORM = "未记录平台";

    /** 左侧快捷日期列表：最近 N 天（含今天） */
    public static final int SIDEBAR_IMPORT_DAYS = 10;

    /** 分单日期选择/查询的历史窗口（天，含今天，最多一年） */
    public static final int MAX_IMPORT_HISTORY_DAYS = 365;

    /** 单次查询日期区间最大跨度（天） */
    public static final int MAX_IMPORT_RANGE_SPAN_DAYS = 365;

    /** 关键字搜索最大长度 */
    private static final int KEYWORD_MAX_LENGTH = 64;

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderPagedQueryService importOrderPagedQueryService;
    private final DailyTableService dailyTableService;

    @Transactional(readOnly = true)
    public List<DailyTableRowDto> listRowsBySystemNos(List<String> systemNos) {
        if (systemNos == null || systemNos.isEmpty()) {
            return List.of();
        }
        List<ImportOrder> orders =
                importOrderRepository.findBySystemNoInOrderByMerchantAscSystemNoAsc(systemNos);
        List<DailyTableRowDto> rows = new ArrayList<>();
        for (ImportOrder order : orders) {
            rows.add(toRowDto(order));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listTodayOrders(Long latestTaskId) {
        return listTodayOrders(latestTaskId, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listTodayOrders(Long latestTaskId, String keyword) {
        return listOrdersByDate(LocalDate.now(ZONE_SHANGHAI), latestTaskId, keyword);
    }

    @Transactional(readOnly = true)
    public List<ImportedDateSummaryDto> listRecentDateSummaries() {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        List<ImportedDateSummaryDto> summaries = new ArrayList<>();
        for (int offset = 0; offset < SIDEBAR_IMPORT_DAYS; offset++) {
            LocalDate day = today.minusDays(offset);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            long count = importOrderRepository.countByIssueDateGreaterThanEqualAndIssueDateLessThan(start, end);
            boolean isToday = offset == 0;
            summaries.add(
                    new ImportedDateSummaryDto(
                            day.toString(), formatDateLabel(day, isToday), (int) count, isToday));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDate(LocalDate date, Long latestTaskId) {
        return listOrdersByDate(date, latestTaskId, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDate(LocalDate date, Long latestTaskId, String keyword) {
        return listOrdersByDate(date, latestTaskId, keyword, null, null, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDate(
            LocalDate date,
            Long latestTaskId,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus) {
        return listOrdersByDate(
                date, latestTaskId, keyword, platform, merchant, receiptStatus, null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDate(
            LocalDate date,
            Long latestTaskId,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales) {
        return listOrdersByDate(
                date,
                latestTaskId,
                keyword,
                platform,
                merchant,
                receiptStatus,
                afterSales,
                null);
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDate(
            LocalDate date,
            Long latestTaskId,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales,
            String afterSalesStatus) {
        LocalDate normalized = requireRecentDate(date);
        return listOrdersByDateRange(
                normalized,
                normalized,
                latestTaskId,
                keyword,
                platform,
                merchant,
                receiptStatus,
                afterSales,
                afterSalesStatus);
    }

    /**
     * 按分单日期区间查询已入库订单（历史最多一年，单次区间跨度最多一年）
     */
    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDateRange(
            LocalDate startDate, LocalDate endDate, Long latestTaskId) {
        return listOrdersByDateRange(startDate, endDate, latestTaskId, null);
    }

    /**
     * 按分单日期区间查询已入库订单（历史最多一年，单次区间跨度最多一年）
     */
    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDateRange(
            LocalDate startDate, LocalDate endDate, Long latestTaskId, String keyword) {
        return listOrdersByDateRange(
                startDate, endDate, latestTaskId, keyword, null, null, null);
    }

    /**
     * 按分单日期区间查询已入库订单（历史最多一年，单次区间跨度最多一年）
     */
    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            Long latestTaskId,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus) {
        return listOrdersByDateRange(
                startDate,
                endDate,
                latestTaskId,
                keyword,
                platform,
                merchant,
                receiptStatus,
                null);
    }

    /**
     * 按分单日期区间查询已入库订单（历史最多一年，单次区间跨度最多一年）
     */
    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            Long latestTaskId,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales) {
        return listOrdersByDateRange(
                startDate,
                endDate,
                latestTaskId,
                keyword,
                platform,
                merchant,
                receiptStatus,
                afterSales,
                null);
    }

    /**
     * 按分单日期区间查询已入库订单（历史最多一年，单次区间跨度最多一年）
     */
    @Transactional(readOnly = true)
    public SplitResultResponse listOrdersByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            Long latestTaskId,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales,
            String afterSalesStatus) {
        LocalDate normalizedStart = requireRecentDate(startDate);
        LocalDate normalizedEnd = requireRecentDate(endDate);
        if (normalizedStart.isAfter(normalizedEnd)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        long rangeSpanDays = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd) + 1;
        if (rangeSpanDays > MAX_IMPORT_RANGE_SPAN_DAYS) {
            throw new BusinessException("日期区间不能超过 " + MAX_IMPORT_RANGE_SPAN_DAYS + " 天");
        }
        LocalDateTime start = normalizedStart.atStartOfDay();
        LocalDateTime end = normalizedEnd.plusDays(1).atStartOfDay();
        String normalizedKeyword = normalizeKeyword(keyword);
        ImportOrderReceiptStatus receipt = parseReceiptStatus(receiptStatus);
        Boolean afterSalesFilter = parseAfterSales(afterSales);
        AfterSalesStatus afterSalesStatusFilter = parseAfterSalesStatus(afterSalesStatus);

        ImportOrderListFilter listFilter =
                new ImportOrderListFilter(
                        start,
                        end,
                        normalizedKeyword,
                        platform,
                        merchant,
                        receipt,
                        afterSalesFilter,
                        afterSalesStatusFilter);
        ImportOrderListFilter merchantSummaryFilter =
                new ImportOrderListFilter(
                        start,
                        end,
                        normalizedKeyword,
                        platform,
                        null,
                        receipt,
                        afterSalesFilter,
                        afterSalesStatusFilter);

        List<ImportOrder> orders = importOrderPagedQueryService.findAllOrders(listFilter);
        List<MerchantSplitGroupDto> merchantGroups =
                importOrderPagedQueryService.summarizeByMerchant(merchantSummaryFilter);
        List<PlatformSummaryDto> platformSummaries =
                importOrderPagedQueryService.summarizeByPlatform(
                        start, end, normalizedKeyword, receipt);

        List<DailyTableRowDto> pageRows = new ArrayList<>();
        for (ImportOrder order : orders) {
            pageRows.add(toRowDto(order));
        }

        Set<String> platformNames = new HashSet<>();
        for (PlatformSummaryDto summary : platformSummaries) {
            platformNames.add(summary.getPlatform());
        }

        String issueDateLabel = formatRangeDateLabel(normalizedStart, normalizedEnd);
        long splittableOrderCount =
                importOrderRepository.countByIssueDateGreaterThanEqualAndIssueDateLessThan(
                        start, end);
        return new SplitResultResponse(
                latestTaskId,
                issueDateLabel,
                pageRows.size(),
                platformNames.size(),
                countRealMerchants(merchantGroups),
                merchantGroups,
                platformSummaries,
                pageRows,
                (int) splittableOrderCount);
    }

    /** 「未定义」为虚拟商家，不计入商家数量 */
    static int countRealMerchants(List<MerchantSplitGroupDto> merchantGroups) {
        if (merchantGroups == null || merchantGroups.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (MerchantSplitGroupDto group : merchantGroups) {
            if (!MerchantConfigService.PENDING_SPLIT_MERCHANT.equals(group.getMerchant())) {
                count++;
            }
        }
        return count;
    }

    private ImportOrderReceiptStatus parseReceiptStatus(String receiptStatus) {
        if (receiptStatus == null || receiptStatus.isBlank()) {
            return null;
        }
        try {
            return ImportOrderReceiptStatus.valueOf(receiptStatus.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("回单状态参数无效");
        }
    }

    private Boolean parseAfterSales(String afterSales) {
        if (afterSales == null || afterSales.isBlank()) {
            return null;
        }
        String normalized = afterSales.trim();
        if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
            return false;
        }
        throw new BusinessException("售后筛选参数无效");
    }

    private AfterSalesStatus parseAfterSalesStatus(String afterSalesStatus) {
        if (afterSalesStatus == null || afterSalesStatus.isBlank()) {
            return null;
        }
        try {
            return AfterSalesStatus.valueOf(afterSalesStatus.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("售后状态筛选参数无效");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException("搜索关键字不能超过 " + KEYWORD_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }

    public LocalDate requireRecentDate(LocalDate date) {
        if (date == null) {
            throw new BusinessException("日期参数无效");
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        LocalDate earliest = today.minusDays(MAX_IMPORT_HISTORY_DAYS - 1L);
        if (date.isBefore(earliest) || date.isAfter(today)) {
            throw new BusinessException("仅支持查询最近 " + MAX_IMPORT_HISTORY_DAYS + " 天内的订单");
        }
        return date;
    }

    private String formatDateLabel(LocalDate date, boolean isToday) {
        if (isToday) {
            return date + "（今天）";
        }
        return date.toString();
    }

    private String formatRangeDateLabel(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            boolean isToday = start.equals(LocalDate.now(ZONE_SHANGHAI));
            return formatDateLabel(start, isToday);
        }
        return start + " ~ " + end;
    }

    private SplitResultResponse buildResponse(
            List<ImportOrder> orders, Long latestTaskId, String issueDateLabel) {
        if (orders.isEmpty()) {
            return emptyResponse(latestTaskId, issueDateLabel);
        }

        List<DailyTableRowDto> pageRows = new ArrayList<>();
        for (ImportOrder order : orders) {
            pageRows.add(toRowDto(order));
        }
        return new SplitResultResponse(
                latestTaskId,
                issueDateLabel,
                pageRows.size(),
                0,
                0,
                List.of(),
                List.of(),
                pageRows,
                0);
    }

    private SplitResultResponse emptyResponse(Long latestTaskId, String issueDateLabel) {
        return new SplitResultResponse(
                latestTaskId,
                issueDateLabel,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                0);
    }

    private String resolvePlatformName(String platform) {
        if (platform == null || platform.isBlank()) {
            return UNKNOWN_PLATFORM;
        }
        return platform.trim();
    }

    public DailyTableRowDto toRowDto(ImportOrder order) {
        boolean needsAfterSales = Boolean.TRUE.equals(order.getAfterSales());
        AfterSalesStatus status = resolveAfterSalesStatus(order.getAfterSalesStatus());
        DailyTableRowDto.DailyTableRowDtoBuilder builder =
                DailyTableRowDto.builder()
                .systemNo(SystemNoGenerator.display(order))
                .platform(resolvePlatformName(order.getPlatform()))
                .merchant(nullToEmpty(order.getMerchant()))
                .orderNo(nullToEmpty(order.getOrderNo()))
                .productName(nullToEmpty(order.getProductName()))
                .spec(nullToEmpty(order.getSpec()))
                .quantity(order.getQuantity() == null ? 0 : order.getQuantity())
                .receiver(nullToEmpty(order.getReceiver()))
                .address(nullToEmpty(order.getAddress()))
                .phone(nullToEmpty(order.getPhone()))
                .shippingFee(
                        order.getShippingFee() == null ? BigDecimal.ZERO : order.getShippingFee())
                .remark(nullToEmpty(order.getRemark()))
                .costPrice(order.getCostPrice())
                .supplyPrice(order.getSupplyPrice())
                .receiptStatus(resolveReceiptStatus(order.getReceiptStatus()))
                .receiptStatusLabel(resolveReceiptStatusLabel(order.getReceiptStatus()))
                .logisticsNo(nullToEmpty(order.getLogisticsNo()))
                .logisticsCompany(nullToEmpty(order.getLogisticsCompany()))
                .issueDate(dailyTableService.formatIssueDate(order.getIssueDate()))
                .afterSales(needsAfterSales)
                .afterSalesStatus(status.name())
                .afterSalesStatusLabel(status.getLabel());
        if (needsAfterSales) {
            builder.afterSalesRemark(nullToEmpty(order.getAfterSalesRemark()))
                    .afterSalesAt(
                            order.getAfterSalesAt() == null
                                    ? ""
                                    : dailyTableService.formatIssueDate(order.getAfterSalesAt()));
        }
        return builder.build();
    }

    private AfterSalesStatus resolveAfterSalesStatus(AfterSalesStatus status) {
        if (status == null) {
            return AfterSalesStatus.NONE;
        }
        return status;
    }

    private String resolveReceiptStatus(ImportOrderReceiptStatus status) {
        if (status == null) {
            return ImportOrderReceiptStatus.PENDING.name();
        }
        return status.name();
    }

    private String resolveReceiptStatusLabel(ImportOrderReceiptStatus status) {
        if (status == null) {
            return ImportOrderReceiptStatus.PENDING.getLabel();
        }
        return status.getLabel();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
