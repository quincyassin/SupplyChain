package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商家/平台对账导出
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ReconcileExportService {

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderQueryService importOrderQueryService;
    private final ExcelWriterService excelWriterService;

    public byte[] exportMerchantReconcile(LocalDate startDate, LocalDate endDate, String merchant)
            throws IOException {
        String normalizedMerchant = normalizeRequiredName(merchant, "商家");
        validateDateRange(startDate, endDate);
        List<DailyTableRowDto> rows =
                loadRows(
                        startDate,
                        endDate,
                        order -> normalizedMerchant.equals(normalizeOptionalName(order.getMerchant())));
        if (rows.isEmpty()) {
            throw new BusinessException("当前日期区间内没有该商家的订单");
        }
        return excelWriterService.writeMerchantReconcileTable(
                normalizedMerchant + "对账", rows);
    }

    public byte[] exportPlatformReconcile(LocalDate startDate, LocalDate endDate, String platform)
            throws IOException {
        String normalizedPlatform = normalizeRequiredName(platform, "平台");
        validateDateRange(startDate, endDate);
        List<DailyTableRowDto> rows =
                loadRows(
                        startDate,
                        endDate,
                        order ->
                                normalizedPlatform.equals(
                                        resolvePlatformName(order.getPlatform())));
        if (rows.isEmpty()) {
            throw new BusinessException("当前日期区间内没有该平台的订单");
        }
        return excelWriterService.writePlatformReconcileTable(normalizedPlatform + "对账", rows);
    }

    private List<DailyTableRowDto> loadRows(
            LocalDate startDate, LocalDate endDate, Predicate<ImportOrder> orderFilter) {
        LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
        LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
        LocalDateTime rangeStart = normalizedStart.atStartOfDay();
        LocalDateTime rangeEndExclusive = normalizedEnd.plusDays(1).atStartOfDay();
        List<ImportOrder> orders =
                importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                rangeStart, rangeEndExclusive);
        List<DailyTableRowDto> rows = new ArrayList<>();
        for (ImportOrder order : orders) {
            if (!orderFilter.test(order)) {
                continue;
            }
            rows.add(importOrderQueryService.toRowDto(order));
        }
        return rows;
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
    }

    private String normalizeRequiredName(String name, String label) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("请选择" + label);
        }
        return name.trim();
    }

    private String normalizeOptionalName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim();
    }

    private String resolvePlatformName(String platform) {
        if (platform == null || platform.isBlank()) {
            return ImportOrderQueryService.UNKNOWN_PLATFORM;
        }
        return platform.trim();
    }
}
