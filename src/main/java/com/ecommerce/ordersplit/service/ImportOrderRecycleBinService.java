package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.ImportOrderRecycleBinOperationResultDto;
import com.ecommerce.ordersplit.dto.MerchantSplitGroupDto;
import com.ecommerce.ordersplit.dto.PlatformSummaryDto;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ImportOrderRecycleBin;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderRecycleBinRepository;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.support.ImportOrderRecycleBinMapper;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单回收站：移入、恢复、彻底删除
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ImportOrderRecycleBinService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final String ORDER_COLUMNS =
            """
            system_no, task_id, merchant, merchant_split, platform, order_no, product_name, spec, quantity,
            receiver, address, phone, shipping_fee, remark, cost_price, supply_price, receipt_status,
            logistics_no, logistics_company, after_sales, after_sales_remark, after_sales_at, after_sales_status,
            issue_date, source_row_num, created_at
            """;

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderRecycleBinRepository importOrderRecycleBinRepository;
    private final ImportOrderRecycleBinPagedQueryService importOrderRecycleBinPagedQueryService;
    private final ImportOrderQueryService importOrderQueryService;
    private final DailyTableService dailyTableService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 将主表订单移入回收站
     */
    @Transactional
    public int moveOrdersToRecycleBin(List<ImportOrder> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        List<String> systemNos = new ArrayList<>();
        for (ImportOrder entity : entities) {
            systemNos.add(entity.getSystemNo());
        }
        LocalDateTime deletedAt = LocalDateTime.now(ZONE_SHANGHAI);

        String insertSql =
                "INSERT INTO import_order_recycle_bin ("
                        + ORDER_COLUMNS
                        + ", deleted_at) SELECT "
                        + ORDER_COLUMNS
                        + ", :deletedAt FROM import_order"
                        + " WHERE system_no IN :systemNos";
        Query insertQuery = entityManager.createNativeQuery(insertSql);
        insertQuery.setParameter("deletedAt", deletedAt);
        insertQuery.setParameter("systemNos", systemNos);
        int inserted = insertQuery.executeUpdate();

        Query deleteQuery =
                entityManager.createNativeQuery(
                        "DELETE FROM import_order WHERE system_no IN :systemNos");
        deleteQuery.setParameter("systemNos", systemNos);
        int deleted = deleteQuery.executeUpdate();

        if (inserted != deleted) {
            throw new BusinessException("移入回收站失败：搬移条数不一致，已回滚");
        }
        return inserted;
    }

    @Transactional(readOnly = true)
    public SplitResultResponse listRecycleBinOrdersByDateRange(
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

        List<ImportOrderRecycleBin> recycleBinOrders =
                importOrderRecycleBinPagedQueryService.findAllOrders(listFilter);
        List<MerchantSplitGroupDto> merchantGroups =
                importOrderRecycleBinPagedQueryService.summarizeByMerchant(merchantSummaryFilter);
        List<PlatformSummaryDto> platformSummaries =
                importOrderRecycleBinPagedQueryService.summarizeByPlatform(
                        start, end, normalizedKeyword, receipt);

        List<DailyTableRowDto> pageRows = new ArrayList<>();
        for (ImportOrderRecycleBin recycleBinOrder : recycleBinOrders) {
            DailyTableRowDto rowDto =
                    importOrderQueryService.toRowDto(
                            ImportOrderRecycleBinMapper.toImportOrder(recycleBinOrder));
            rowDto.setDeletedAt(dailyTableService.formatIssueDate(recycleBinOrder.getDeletedAt()));
            pageRows.add(rowDto);
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

    /**
     * 从回收站恢复选中订单到主表
     */
    @Transactional
    public ImportOrderRecycleBinOperationResultDto restoreSelected(List<String> systemNos) {
        List<ImportOrderRecycleBin> entities = requireRecycleBinOrders(systemNos);
        List<String> normalizedSystemNos = collectSystemNos(entities);
        ensureNoMainTableConflicts(normalizedSystemNos);

        String insertSql =
                "INSERT INTO import_order ("
                        + ORDER_COLUMNS
                        + ") SELECT "
                        + ORDER_COLUMNS
                        + " FROM import_order_recycle_bin"
                        + " WHERE system_no IN :systemNos";
        Query insertQuery = entityManager.createNativeQuery(insertSql);
        insertQuery.setParameter("systemNos", normalizedSystemNos);
        int inserted = insertQuery.executeUpdate();

        Query deleteQuery =
                entityManager.createNativeQuery(
                        "DELETE FROM import_order_recycle_bin WHERE system_no IN :systemNos");
        deleteQuery.setParameter("systemNos", normalizedSystemNos);
        int deleted = deleteQuery.executeUpdate();

        if (inserted != deleted) {
            throw new BusinessException("恢复失败：搬移条数不一致，已回滚");
        }
        return new ImportOrderRecycleBinOperationResultDto(
                inserted, "已恢复 " + inserted + " 条订单");
    }

    /**
     * 从回收站彻底删除选中订单
     */
    @Transactional
    public ImportOrderRecycleBinOperationResultDto purgeSelected(List<String> systemNos) {
        List<ImportOrderRecycleBin> entities = requireRecycleBinOrders(systemNos);
        importOrderRecycleBinRepository.deleteAll(entities);
        return new ImportOrderRecycleBinOperationResultDto(
                entities.size(), "已彻底删除 " + entities.size() + " 条订单");
    }

    private List<ImportOrderRecycleBin> requireRecycleBinOrders(List<String> systemNos) {
        if (systemNos == null || systemNos.isEmpty()) {
            throw new BusinessException("请先勾选要操作的订单");
        }
        Set<String> distinctSystemNos = new LinkedHashSet<>();
        for (String systemNo : systemNos) {
            distinctSystemNos.add(SystemNoGenerator.requireValid(systemNo));
        }
        List<ImportOrderRecycleBin> entities =
                importOrderRecycleBinRepository.findBySystemNoInOrderByDeletedAtDescSystemNoDesc(
                        new ArrayList<>(distinctSystemNos));
        if (entities.isEmpty()) {
            throw new BusinessException("未找到选中的订单，请刷新后重试");
        }
        if (entities.size() != distinctSystemNos.size()) {
            throw new BusinessException("部分选中订单不存在或已处理，请刷新后重选");
        }
        return entities;
    }

    private List<String> collectSystemNos(List<ImportOrderRecycleBin> entities) {
        List<String> systemNos = new ArrayList<>();
        for (ImportOrderRecycleBin entity : entities) {
            systemNos.add(entity.getSystemNo());
        }
        return systemNos;
    }

    private void ensureNoMainTableConflicts(List<String> systemNos) {
        List<ImportOrder> conflicts =
                importOrderRepository.findBySystemNoInOrderByMerchantAscSystemNoAsc(systemNos);
        if (conflicts.isEmpty()) {
            return;
        }
        throw new BusinessException(
                "恢复失败：主表已存在 "
                        + conflicts.size()
                        + " 条相同系统编号的订单（例如 "
                        + conflicts.get(0).getSystemNo()
                        + "），请先处理冲突");
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("请选择日期区间");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        if (endDate.isAfter(today)) {
            throw new BusinessException("结束日期不能晚于今天");
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
}
