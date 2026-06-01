package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ProductPrice;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 商品价格仓储
 *
 * @author huangxinsong
 */
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    Optional<ProductPrice> findByPlatformAndProductNameAndSpec(
            String platform, String productName, String spec);

    List<ProductPrice> findByProductNameIn(Collection<String> productNames);

    List<ProductPrice> findByProductNameAndSpec(String productName, String spec);

    int deleteByPlatformAndProductNameAndSpec(String platform, String productName, String spec);

    @Query(
            """
            SELECT p FROM ProductPrice p
            WHERE (:keywordPattern IS NULL
                OR p.productName LIKE :keywordPattern ESCAPE '\\'
                OR p.platform LIKE :keywordPattern ESCAPE '\\')
            ORDER BY p.updatedAt DESC, p.productName ASC, p.spec ASC
            """)
    List<ProductPrice> search(@Param("keywordPattern") String keywordPattern);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE product_price
                    SET cost_price = :costPrice, updated_at = CURRENT_TIMESTAMP
                    WHERE product_name = :productName AND spec = :spec
                    """,
            nativeQuery = true)
    int updateCostPriceByProductKey(
            @Param("productName") String productName,
            @Param("spec") String spec,
            @Param("costPrice") BigDecimal costPrice);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO product_price (platform, product_name, spec, cost_price, supply_price, updated_at)
                    VALUES (:platform, :productName, :spec, :costPrice, :supplyPrice, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        cost_price = COALESCE(VALUES(cost_price), cost_price),
                        supply_price = COALESCE(VALUES(supply_price), supply_price),
                        updated_at = CURRENT_TIMESTAMP
                    """,
            nativeQuery = true)
    void upsert(
            @Param("platform") String platform,
            @Param("productName") String productName,
            @Param("spec") String spec,
            @Param("costPrice") BigDecimal costPrice,
            @Param("supplyPrice") BigDecimal supplyPrice);
}
