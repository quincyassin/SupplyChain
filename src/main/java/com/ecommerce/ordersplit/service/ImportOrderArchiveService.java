package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.ImportOrderArchiveOperationResultDto;
import com.ecommerce.ordersplit.dto.ImportOrderArchivePreviewDto;
import com.ecommerce.ordersplit.dto.ImportedDateSummaryDto;
import com.ecommerce.ordersplit.dto.MerchantSplitGroupDto;
import com.ecommerce.ordersplit.dto.PlatformSummaryDto;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.entity.ImportOrderArchive;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderArchiveRepository;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.support.ImportOrderArchiveMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Date;
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
 * 订单物理归档与恢复
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ImportOrderArchiveService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final String ORDER_COLUMNS =
            """
            system_no, task_id, merchant, merchant_split, platform, order_no, product_name, spec, quantity,
            receiver, address, phone, shipping_fee, remark, cost_price, supply_price, receipt_status,
            logistics_no, logistics_company, after_sales, after_sales_remark, after_sales_at, after_sales_status,
            issue_date, source_row_num, created_at
            """;

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderArchiveRepository importOrderArchiveRepository;
    private final ImportOrderArchivePagedQueryService importOrderArchivePagedQueryService;
    private final ImportOrderQueryService importOrderQueryService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public ImportOrderArchivePreviewDto previewArchive(LocalDate startDate, LocalDate endDate) {
        validateArchiveDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        long orderCount =
                importOrderRepository.countByIssueDateGreaterThanEqualAndIssueDateLessThan(start, end);
        long pendingCount =
                importOrderRepository.countInIssueDateRangeAndAfterSalesStatus(
                        start, end, AfterSalesStatus.PENDING);
        long completedCount =
                importOrderRepository.countInIssueDateRangeAndAfterSalesStatus(
                        start, end, AfterSalesStatus.COMPLETED);
        return new ImportOrderArchivePreviewDto(
                formatRangeDateLabel(startDate, endDate), orderCount, pendingCount, completedCount);
    }

    @Transactional
    public ImportOrderArchiveOperationResultDto archiveDateRange(
            LocalDate startDate, LocalDate endDate) {
        validateArchiveDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        long orderCount =
                importOrderRepository.countByIssueDateGreaterThanEqualAndIssueDateLessThan(start, end);
        if (orderCount <= 0) {
            throw new BusinessException("所选日期区间内没有可归档的订单");
        }

        String insertSql =
                "INSERT INTO import_order_archive ("
                        + ORDER_COLUMNS
                        + ") SELECT "
                        + ORDER_COLUMNS
                        + " FROM import_order"
                        + " WHERE issue_date >= :startInclusive AND issue_date < :endExclusive";
        Query insertQuery = entityManager.createNativeQuery(insertSql);
        insertQuery.setParameter("startInclusive", start);
        insertQuery.setParameter("endExclusive", end);
        int inserted = insertQuery.executeUpdate();

        Query deleteQuery =
                entityManager.createNativeQuery(
                        """
                        DELETE FROM import_order
                        WHERE issue_date >= :startInclusive AND issue_date < :endExclusive
                        """);
        deleteQuery.setParameter("startInclusive", start);
        deleteQuery.setParameter("endExclusive", end);
        int deleted = deleteQuery.executeUpdate();

        if (inserted != deleted) {
            throw new BusinessException("归档失败：搬移条数不一致，已回滚");
        }
        return new ImportOrderArchiveOperationResultDto(
                inserted,
                "已归档 "
                        + inserted
                        + " 条订单（"
                        + formatRangeDateLabel(startDate, endDate)
                        + "）");
    }

    @Transactional(readOnly = true)
    public List<ImportedDateSummaryDto> listArchivedDateSummaries() {
        List<Object[]> rows = importOrderArchiveRepository.summarizeByIssueDate();
        List<ImportedDateSummaryDto> summaries = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate issueDay = toLocalDate(row[0]);
            if (issueDay == null) {
                continue;
            }
            int rowCount = row[1] == null ? 0 : ((Number) row[1]).intValue();
            summaries.add(
                    new ImportedDateSummaryDto(issueDay.toString(), issueDay.toString(), rowCount, false));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listArchivedOrdersByDateRange(
            LocalDate startDate,
            LocalDate endDate,
            String keyword,
            String platform,
            String merchant,
            String receiptStatus,
            String afterSales,
            String afterSalesStatus) {
        validateDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        String normalizedKeyword = importOrderQueryService.normalizeSearchKeyword(keyword);
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

        List<ImportOrderArchive> archives =
                importOrderArchivePagedQueryService.findAllOrders(listFilter);
        List<MerchantSplitGroupDto> merchantGroups =
                importOrderArchivePagedQueryService.summarizeByMerchant(merchantSummaryFilter);
        List<PlatformSummaryDto> platformSummaries =
                importOrderArchivePagedQueryService.summarizeByPlatform(
                        start, end, normalizedKeyword, receipt);

        List<DailyTableRowDto> pageRows = new ArrayList<>();
        for (ImportOrderArchive archive : archives) {
            pageRows.add(
                    importOrderQueryService.toRowDto(
                            ImportOrderArchiveMapper.toImportOrder(archive)));
        }

        Set<String> platformNames = new HashSet<>();
        for (PlatformSummaryDto summary : platformSummaries) {
            platformNames.add(summary.getPlatform());
        }

        String issueDateLabel = formatRangeDateLabel(startDate, endDate);
        return new SplitResultResponse(
                null,
                issueDateLabel,
                pageRows.size(),
                platformNames.size(),
                ImportOrderQueryService.countRealMerchants(merchantGroups),
                merchantGroups,
                platformSummaries,
                pageRows,
                pageRows.size());
    }

    @Transactional(readOnly = true)
    public ImportOrderArchivePreviewDto previewRestore(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        long orderCount = importOrderArchiveRepository.countInIssueDateRange(start, end);
        long pendingCount =
                countArchivedInRangeByStatus(start, end, AfterSalesStatus.PENDING.name());
        long completedCount =
                countArchivedInRangeByStatus(start, end, AfterSalesStatus.COMPLETED.name());
        return new ImportOrderArchivePreviewDto(
                startDate + " ~ " + endDate, orderCount, pendingCount, completedCount);
    }

    @Transactional
    public ImportOrderArchiveOperationResultDto restoreDateRange(
            LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        long orderCount = importOrderArchiveRepository.countInIssueDateRange(start, end);
        if (orderCount <= 0) {
            throw new BusinessException("所选日期区间内没有可恢复的归档订单");
        }

        List<String> conflicts =
                importOrderArchiveRepository.findConflictSystemNosInRange(start, end);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(
                    "恢复失败：主表已存在 "
                            + conflicts.size()
                            + " 条相同系统编号的订单（例如 "
                            + conflicts.get(0)
                            + "），请先处理冲突");
        }

        String insertSql =
                "INSERT INTO import_order ("
                        + ORDER_COLUMNS
                        + ") SELECT "
                        + ORDER_COLUMNS
                        + " FROM import_order_archive"
                        + " WHERE issue_date >= :startInclusive AND issue_date < :endExclusive";
        Query insertQuery = entityManager.createNativeQuery(insertSql);
        insertQuery.setParameter("startInclusive", start);
        insertQuery.setParameter("endExclusive", end);
        int inserted = insertQuery.executeUpdate();

        Query deleteQuery =
                entityManager.createNativeQuery(
                        """
                        DELETE FROM import_order_archive
                        WHERE issue_date >= :startInclusive AND issue_date < :endExclusive
                        """);
        deleteQuery.setParameter("startInclusive", start);
        deleteQuery.setParameter("endExclusive", end);
        int deleted = deleteQuery.executeUpdate();

        if (inserted != deleted) {
            throw new BusinessException("恢复失败：搬移条数不一致，已回滚");
        }
        return new ImportOrderArchiveOperationResultDto(
                inserted,
                "已恢复 "
                        + inserted
                        + " 条订单到主表（"
                        + startDate
                        + " ~ "
                        + endDate
                        + "）");
    }

    private long countArchivedInRangeByStatus(
            LocalDateTime startInclusive, LocalDateTime endExclusive, String status) {
        Query query =
                entityManager.createNativeQuery(
                        """
                        SELECT COUNT(*) FROM import_order_archive o
                        WHERE o.issue_date >= :startInclusive
                          AND o.issue_date < :endExclusive
                          AND o.after_sales_status = :status
                        """);
        query.setParameter("startInclusive", startInclusive);
        query.setParameter("endExclusive", endExclusive);
        query.setParameter("status", status);
        Number count = (Number) query.getSingleResult();
        return count == null ? 0L : count.longValue();
    }

    private void validateArchiveDateRange(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        if (endDate.isAfter(today)) {
            throw new BusinessException("归档结束日期不能晚于今天");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("请选择日期区间");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        long rangeSpanDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (rangeSpanDays > ImportOrderQueryService.MAX_IMPORT_RANGE_SPAN_DAYS) {
            throw new BusinessException(
                    "日期区间不能超过 " + ImportOrderQueryService.MAX_IMPORT_RANGE_SPAN_DAYS + " 天");
        }
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

    private String formatRangeDateLabel(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            return start.toString();
        }
        return start + " ~ " + end;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(ZONE_SHANGHAI).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
