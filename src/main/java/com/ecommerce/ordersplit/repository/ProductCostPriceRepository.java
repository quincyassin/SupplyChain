package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ProductCostPrice;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 商品成本价仓储
 *
 * @author huangxinsong
 */
public interface ProductCostPriceRepository extends JpaRepository<ProductCostPrice, Long> {

    Optional<ProductCostPrice> findByProductNameAndSpec(String productName, String spec);

    List<ProductCostPrice> findByProductNameIn(Collection<String> productNames);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO product_cost_price (product_name, spec, cost_price, updated_at)
                    VALUES (:productName, :spec, :costPrice, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        cost_price = :costPrice,
                        updated_at = CURRENT_TIMESTAMP
                    """,
            nativeQuery = true)
    void upsertCostPrice(
            @Param("productName") String productName,
            @Param("spec") String spec,
            @Param("costPrice") BigDecimal costPrice);
}
