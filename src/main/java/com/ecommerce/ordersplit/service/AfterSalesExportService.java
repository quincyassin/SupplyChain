package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 售后订单导出
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class AfterSalesExportService {

    private static final Comparator<DailyTableRowDto> AFTER_SALES_STATUS_COMPARATOR =
            Comparator.comparingInt(AfterSalesExportService::afterSalesStatusSortOrder)
                    .thenComparing(
                            DailyTableRowDto::getAfterSalesAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                            row -> nullToEmpty(row.getSystemNo()),
                            Comparator.nullsLast(String::compareTo));

    private final ImportOrderPagedQueryService importOrderPagedQueryService;
    private final ImportOrderQueryService importOrderQueryService;
    private final ExcelWriterService excelWriterService;

    public byte[] exportAfterSalesOrders(
            LocalDate startDate, LocalDate endDate, String keyword) throws IOException {
        validateDateRange(startDate, endDate);
        LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
        LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
        LocalDateTime rangeStart = normalizedStart.atStartOfDay();
        LocalDateTime rangeEndExclusive = normalizedEnd.plusDays(1).atStartOfDay();
        String normalizedKeyword = importOrderQueryService.normalizeSearchKeyword(keyword);

        List<ImportOrder> orders =
                importOrderPagedQueryService.findAllOrders(
                        new ImportOrderListFilter(
                                rangeStart,
                                rangeEndExclusive,
                                normalizedKeyword,
                                null,
                                null,
                                null,
                                true,
                                null));

        List<DailyTableRowDto> rows = new ArrayList<>();
        for (ImportOrder order : orders) {
            AfterSalesStatus status = order.getAfterSalesStatus();
            if (status == null || status == AfterSalesStatus.NONE) {
                continue;
            }
            rows.add(importOrderQueryService.toRowDto(order));
        }
        rows.sort(AFTER_SALES_STATUS_COMPARATOR);

        if (rows.isEmpty()) {
            throw new BusinessException("当前筛选条件下暂无售后订单");
        }
        return excelWriterService.writeAfterSalesTable(rows);
    }

    static int afterSalesStatusSortOrder(DailyTableRowDto row) {
        if (row == null || row.getAfterSalesStatus() == null) {
            return Integer.MAX_VALUE;
        }
        return switch (row.getAfterSalesStatus()) {
            case "PENDING" -> 0;
            case "COMPLETED" -> 1;
            default -> 2;
        };
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("请选择日期区间");
        }
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
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
