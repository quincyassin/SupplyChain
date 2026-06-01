package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ImportOrderArchive;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 归档订单仓储
 *
 * @author huangxinsong
 */
public interface ImportOrderArchiveRepository extends JpaRepository<ImportOrderArchive, String> {

    @Query("""
            SELECT COUNT(o) FROM ImportOrderArchive o
            WHERE o.issueDate < :endExclusive
            """)
    long countBeforeIssueDate(@Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
            SELECT COUNT(o) FROM ImportOrderArchive o
            WHERE o.issueDate < :endExclusive
              AND o.afterSalesStatus = :status
            """)
    long countBeforeIssueDateAndAfterSalesStatus(
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("status") AfterSalesStatus status);

    @Query("""
            SELECT COUNT(o) FROM ImportOrderArchive o
            WHERE o.issueDate >= :startInclusive
              AND o.issueDate < :endExclusive
            """)
    long countInIssueDateRange(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = """
            SELECT DATE(o.issue_date) AS issue_day, COUNT(*) AS row_count
            FROM import_order_archive o
            GROUP BY DATE(o.issue_date)
            ORDER BY issue_day DESC
            """, nativeQuery = true)
    List<Object[]> summarizeByIssueDate();

    @Query(value = """
            SELECT a.system_no
            FROM import_order_archive a
            INNER JOIN import_order m ON m.system_no = a.system_no
            WHERE a.issue_date >= :startInclusive
              AND a.issue_date < :endExclusive
            LIMIT 20
            """, nativeQuery = true)
    List<String> findConflictSystemNosInRange(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}
