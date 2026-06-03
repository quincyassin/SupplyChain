package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.MerchantSplitGroupDto;
import com.ecommerce.ordersplit.dto.PlatformSummaryDto;
import com.ecommerce.ordersplit.entity.ImportOrderArchive;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.util.SqlLikeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 归档订单分页与汇总查询
 *
 * @author huangxinsong
 */
@Service
public class ImportOrderArchivePagedQueryService {

    private static final String KEYWORD_WHERE = """
             AND (
               o.merchant LIKE :likePattern
               OR IFNULL(o.platform, '') LIKE :likePattern
               OR IFNULL(o.system_no, '') LIKE :likePattern
               OR IFNULL(o.logistics_no, '') LIKE :likePattern
               OR IFNULL(o.order_no, '') LIKE :likePattern
             )
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public long countOrders(ImportOrderListFilter filter) {
        StringBuilder sql =
                new StringBuilder("SELECT COUNT(*) FROM import_order_archive o WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendFilters(sql, params, filter);
        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);
        Number total = (Number) query.getSingleResult();
        return total == null ? 0L : total.longValue();
    }

    @SuppressWarnings("unchecked")
    public List<ImportOrderArchive> findAllOrders(ImportOrderListFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT o.* FROM import_order_archive o WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendFilters(sql, params, filter);
        sql.append(" ORDER BY o.created_at DESC, o.system_no DESC");

        Query query = entityManager.createNativeQuery(sql.toString(), ImportOrderArchive.class);
        bindParams(query, params);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<MerchantSplitGroupDto> summarizeByMerchant(ImportOrderListFilter filter) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT o.merchant,
                          COUNT(*) AS row_count,
                          SUM(CASE WHEN o.receipt_status = 'RECEIPTED' THEN 1 ELSE 0 END) AS receipted_count
                        FROM import_order_archive o WHERE 1=1""");
        Map<String, Object> params = new HashMap<>();
        appendFilters(sql, params, filter);
        sql.append(" GROUP BY o.merchant ORDER BY o.merchant ASC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);
        List<Object[]> rows = query.getResultList();
        List<MerchantSplitGroupDto> summaries = new ArrayList<>();
        for (Object[] row : rows) {
            String merchant = row[0] == null ? "" : row[0].toString();
            int rowCount = row[1] == null ? 0 : ((Number) row[1]).intValue();
            int receiptedCount = row[2] == null ? 0 : ((Number) row[2]).intValue();
            summaries.add(new MerchantSplitGroupDto(merchant, rowCount, receiptedCount, List.of()));
        }
        return summaries;
    }

    @SuppressWarnings("unchecked")
    public List<PlatformSummaryDto> summarizeByPlatform(
            java.time.LocalDateTime startInclusive,
            java.time.LocalDateTime endExclusive,
            String keyword,
            ImportOrderReceiptStatus receiptStatus) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT
                          CASE
                            WHEN o.platform IS NULL OR TRIM(o.platform) = '' THEN :unknownPlatform
                            ELSE o.platform
                          END AS platform_name,
                          COUNT(*) AS row_count,
                          SUM(CASE WHEN o.receipt_status = 'RECEIPTED' THEN 1 ELSE 0 END) AS receipted_count
                        FROM import_order_archive o
                        WHERE o.issue_date >= :startInclusive
                          AND o.issue_date < :endExclusive
                        """);
        Map<String, Object> params = new HashMap<>();
        params.put("unknownPlatform", ImportOrderQueryService.UNKNOWN_PLATFORM);
        params.put("startInclusive", startInclusive);
        params.put("endExclusive", endExclusive);
        appendKeyword(sql, params, keyword);
        appendReceiptStatus(sql, params, receiptStatus);
        sql.append(" GROUP BY platform_name ORDER BY platform_name ASC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);
        List<Object[]> rows = query.getResultList();
        List<PlatformSummaryDto> summaries = new ArrayList<>();
        for (Object[] row : rows) {
            String platform =
                    row[0] == null ? ImportOrderQueryService.UNKNOWN_PLATFORM : row[0].toString();
            int rowCount = row[1] == null ? 0 : ((Number) row[1]).intValue();
            int receiptedCount = row[2] == null ? 0 : ((Number) row[2]).intValue();
            summaries.add(new PlatformSummaryDto(platform, rowCount, receiptedCount));
        }
        return summaries;
    }

    private void appendFilters(
            StringBuilder sql, Map<String, Object> params, ImportOrderListFilter filter) {
        sql.append(" AND o.issue_date >= :startInclusive AND o.issue_date < :endExclusive");
        params.put("startInclusive", filter.startInclusive());
        params.put("endExclusive", filter.endExclusive());
        appendKeyword(sql, params, filter.keyword());
        appendPlatform(sql, params, filter.platform());
        appendMerchant(sql, params, filter.merchant());
        appendReceiptStatus(sql, params, filter.receiptStatus());
        appendAfterSalesFilter(sql, params, filter.afterSales(), filter.afterSalesStatus());
    }

    private void appendKeyword(StringBuilder sql, Map<String, Object> params, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String likePattern = SqlLikeUtil.toContainsPattern(keyword.trim());
        if (likePattern == null) {
            return;
        }
        sql.append(KEYWORD_WHERE);
        params.put("likePattern", likePattern);
    }

    private void appendPlatform(StringBuilder sql, Map<String, Object> params, String platform) {
        if (platform == null || platform.isBlank()) {
            return;
        }
        if (ImportOrderQueryService.UNKNOWN_PLATFORM.equals(platform)) {
            sql.append(" AND (o.platform IS NULL OR TRIM(o.platform) = '')");
            return;
        }
        sql.append(" AND o.platform = :platform");
        params.put("platform", platform.trim());
    }

    private void appendMerchant(StringBuilder sql, Map<String, Object> params, String merchant) {
        if (merchant == null || merchant.isBlank()) {
            return;
        }
        sql.append(" AND o.merchant = :merchant");
        params.put("merchant", merchant.trim());
    }

    private void appendReceiptStatus(
            StringBuilder sql, Map<String, Object> params, ImportOrderReceiptStatus receiptStatus) {
        if (receiptStatus == null) {
            return;
        }
        sql.append(" AND o.receipt_status = :receiptStatus");
        params.put("receiptStatus", receiptStatus.name());
    }

    private void appendAfterSalesFilter(
            StringBuilder sql,
            Map<String, Object> params,
            Boolean afterSales,
            AfterSalesStatus afterSalesStatus) {
        if (afterSalesStatus != null) {
            sql.append(" AND o.after_sales_status = :afterSalesStatus");
            params.put("afterSalesStatus", afterSalesStatus.name());
            return;
        }
        if (afterSales == null) {
            return;
        }
        if (afterSales) {
            sql.append(" AND o.after_sales = 1");
            return;
        }
        sql.append(" AND (o.after_sales = 0 OR o.after_sales IS NULL)");
    }

    private void bindParams(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }
}
