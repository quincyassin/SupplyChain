package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ProductSupplyPrice;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 商品供货价仓储
 *
 * @author huangxinsong
 */
public interface ProductSupplyPriceRepository extends JpaRepository<ProductSupplyPrice, Long> {

    Optional<ProductSupplyPrice> findByProductNameAndSpecAndPlatform(
            String productName, String spec, String platform);

    List<ProductSupplyPrice> findByProductNameIn(Collection<String> productNames);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO product_supply_price (product_name, spec, platform, supply_price, updated_at)
                    VALUES (:productName, :spec, :platform, :supplyPrice, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        supply_price = :supplyPrice,
                        updated_at = CURRENT_TIMESTAMP
                    """,
            nativeQuery = true)
    void upsertSupplyPrice(
            @Param("productName") String productName,
            @Param("spec") String spec,
            @Param("platform") String platform,
            @Param("supplyPrice") BigDecimal supplyPrice);
}
