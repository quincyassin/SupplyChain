package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 导入订单仓储
 *
 * @author huangxinsong
 */
public interface ImportOrderRepository extends JpaRepository<ImportOrder, String> {

    List<ImportOrder> findByTaskIdOrderByMerchantAscSystemNoAsc(Long taskId);

    List<ImportOrder> findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
            LocalDateTime startInclusive, LocalDateTime endExclusive);

    /**
     * 查询尚未归属具体商家的订单（用于新增/调整商家配置后批量重分单）
     */
    @Query("""
            SELECT o FROM ImportOrder o
            WHERE o.merchant IS NULL
               OR o.merchant = ''
               OR o.merchant = :pendingMerchant
               OR o.merchant = :unmatchedMerchant
            ORDER BY o.issueDate ASC, o.platform ASC, o.systemNo ASC
            """)
    List<ImportOrder> findOrdersWithoutAssignedMerchant(
            @Param("pendingMerchant") String pendingMerchant,
            @Param("unmatchedMerchant") String unmatchedMerchant);

    List<ImportOrder> findByIssueDateGreaterThanEqualAndIssueDateLessThanAndMerchantOrderByPlatformAscSystemNoAsc(
            LocalDateTime startInclusive, LocalDateTime endExclusive, String merchant);

    /**
     * 查询分单日期区间内可参与按商家分单的订单：未分单过，或已分单但商家为空/未定义
     */
    @Query("""
            SELECT o FROM ImportOrder o
            WHERE o.issueDate >= :startInclusive
              AND o.issueDate < :endExclusive
              AND (
                o.merchantSplit = false
                OR o.merchant = :pendingMerchant
                OR o.merchant IS NULL
                OR o.merchant = ''
              )
            ORDER BY o.platform ASC, o.systemNo ASC
            """)
    List<ImportOrder> findEligibleForMerchantSplitInIssueDateRange(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("pendingMerchant") String pendingMerchant);

    @Query("""
            SELECT COUNT(o) FROM ImportOrder o
            WHERE o.issueDate >= :startInclusive
              AND o.issueDate < :endExclusive
              AND (
                o.merchantSplit = false
                OR o.merchant = :pendingMerchant
                OR o.merchant IS NULL
                OR o.merchant = ''
              )
            """)
    long countEligibleForMerchantSplitInIssueDateRange(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("pendingMerchant") String pendingMerchant);

    List<ImportOrder> findBySystemNoInOrderByMerchantAscSystemNoAsc(Collection<String> systemNos);

    List<ImportOrder> findByMerchantOrderByIssueDateAscPlatformAscMerchantAscSystemNoAsc(
            String merchant);

    long countByMerchant(String merchant);

    long countByIssueDateGreaterThanEqualAndIssueDateLessThan(
            LocalDateTime startInclusive, LocalDateTime endExclusive);

    long countByIssueDateGreaterThanEqualAndIssueDateLessThanAndReceiptStatus(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            ImportOrderReceiptStatus receiptStatus);

    List<ImportOrder> findByOrderNoInAndIssueDateGreaterThanEqualAndIssueDateLessThanOrderBySystemNoAsc(
            Collection<String> orderNos,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive);

    @Query("""
            SELECT DISTINCT o.orderNo FROM ImportOrder o
            WHERE o.orderNo IN :orderNos
              AND o.orderNo IS NOT NULL
              AND o.orderNo <> ''
            """)
    List<String> findExistingOrderNos(@Param("orderNos") Collection<String> orderNos);

    /**
     * 按分单日期区间 + 关键字模糊查询（商家、平台、系统编号、物流单号、订单编号）
     */
    @Query(value = """
            SELECT o.* FROM import_order o
            WHERE o.issue_date >= :startInclusive
              AND o.issue_date < :endExclusive
              AND (
                o.merchant LIKE :likePattern
                OR IFNULL(o.platform, '') LIKE :likePattern
                OR IFNULL(o.system_no, '') LIKE :likePattern
                OR IFNULL(o.logistics_no, '') LIKE :likePattern
                OR IFNULL(o.order_no, '') LIKE :likePattern
              )
            ORDER BY o.platform ASC, o.merchant ASC, o.system_no ASC
            """, nativeQuery = true)
    List<ImportOrder> searchByIssueDateRangeAndKeyword(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("likePattern") String likePattern);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ImportOrder o
            SET o.costPrice = :costPrice
            WHERE o.productName = :productName
              AND COALESCE(o.spec, '') = :spec
            """)
    int updateCostPriceByProductKey(
            @Param("productName") String productName,
            @Param("spec") String spec,
            @Param("costPrice") java.math.BigDecimal costPrice);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ImportOrder o
            SET o.supplyPrice = :supplyPrice
            WHERE o.productName = :productName
              AND COALESCE(o.spec, '') = :spec
              AND COALESCE(o.platform, '') = :platform
            """)
    int updateSupplyPriceByProductPlatformKey(
            @Param("productName") String productName,
            @Param("spec") String spec,
            @Param("platform") String platform,
            @Param("supplyPrice") java.math.BigDecimal supplyPrice);

    @Query("""
            SELECT COUNT(o) FROM ImportOrder o
            WHERE o.issueDate >= :startInclusive
              AND o.issueDate < :endExclusive
              AND o.afterSalesStatus = :status
            """)
    long countInIssueDateRangeAndAfterSalesStatus(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("status") AfterSalesStatus status);
}
