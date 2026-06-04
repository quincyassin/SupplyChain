package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.entity.ProductPrice;
import com.ecommerce.ordersplit.repository.ProductPriceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从当前订单与归档订单汇总可维护价格的商品维度（平台 + 商品名称 + 规格）
 *
 * @author huangxinsong
 */
@Service
public class OrderProductCatalogService {

    private static final String COMBINED_ORDERS_SUBQUERY =
            """
                SELECT TRIM(COALESCE(o.platform, '')) AS platform,
                       TRIM(o.product_name) AS product_name,
                       TRIM(COALESCE(o.spec, '')) AS spec
                FROM import_order o
                WHERE o.product_name IS NOT NULL
                  AND TRIM(o.product_name) <> ''
                UNION
                SELECT TRIM(COALESCE(o.platform, '')) AS platform,
                       TRIM(o.product_name) AS product_name,
                       TRIM(COALESCE(o.spec, '')) AS spec
                FROM import_order_archive o
                WHERE o.product_name IS NOT NULL
                  AND TRIM(o.product_name) <> ''
            """;

    /** 无关键字时使用 DISTINCT_PRODUCTS_SQL_ALL，避免无意义的 LIKE 条件 */
    private static final String DISTINCT_PRODUCTS_SQL_ALL =
            """
            SELECT combined.platform, combined.product_name, combined.spec
            FROM (
            """
                    + COMBINED_ORDERS_SUBQUERY
                    + """
            ) combined
            GROUP BY combined.platform, combined.product_name, combined.spec
            ORDER BY combined.product_name ASC, combined.spec ASC, combined.platform ASC
            """;

    private static final String DISTINCT_PRODUCTS_SQL_FILTERED =
            """
            SELECT combined.platform, combined.product_name, combined.spec
            FROM (
            """
                    + COMBINED_ORDERS_SUBQUERY
                    + """
            ) combined
            WHERE combined.product_name LIKE :productNamePattern
               OR combined.platform LIKE :platformPattern
            GROUP BY combined.platform, combined.product_name, combined.spec
            ORDER BY combined.product_name ASC, combined.spec ASC, combined.platform ASC
            """;

    private final EntityManager entityManager;
    private final ProductPriceService productPriceService;
    private final ProductPriceRepository productPriceRepository;

    public OrderProductCatalogService(
            EntityManager entityManager,
            ProductPriceService productPriceService,
            ProductPriceRepository productPriceRepository) {
        this.entityManager = entityManager;
        this.productPriceService = productPriceService;
        this.productPriceRepository = productPriceRepository;
    }

    public record OrderProductKey(String platform, String productName, String spec) {}

    public record OrderProductPriceRow(
            String platform, String productName, String spec, BigDecimal costPrice, BigDecimal supplyPrice) {}

    @Transactional(readOnly = true)
    public List<OrderProductKey> listDistinctProducts(String keywordPattern) {
        Query query;
        if (keywordPattern == null) {
            query = entityManager.createNativeQuery(DISTINCT_PRODUCTS_SQL_ALL);
        } else {
            query = entityManager.createNativeQuery(DISTINCT_PRODUCTS_SQL_FILTERED);
            query.setParameter("productNamePattern", keywordPattern);
            query.setParameter("platformPattern", keywordPattern);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::toOrderProductKey).toList();
    }

    @Transactional(readOnly = true)
    public Set<String> loadAllOrderProductKeys() {
        Set<String> keys = new HashSet<>();
        for (OrderProductKey key : listDistinctProducts(null)) {
            keys.add(toCompositeKey(key));
        }
        return keys;
    }

    @Transactional(readOnly = true)
    public List<OrderProductPriceRow> listDistinctProductsWithPrices(String keywordPattern) {
        List<OrderProductKey> keys = listDistinctProducts(keywordPattern);
        if (keys.isEmpty()) {
            return List.of();
        }
        Set<String> productNames = new HashSet<>();
        for (OrderProductKey key : keys) {
            productNames.add(key.productName());
        }
        Map<String, BigDecimal> costByProductSpec = new HashMap<>();
        Map<String, BigDecimal> supplyByProductSpecPlatform = new HashMap<>();
        for (ProductPrice row : productPriceRepository.findByProductNameIn(productNames)) {
            String productName = productPriceService.normalizeProductName(row.getProductName());
            String spec = productPriceService.normalizeSpec(row.getSpec());
            String platform = productPriceService.normalizePlatform(row.getPlatform());
            if (row.getCostPrice() != null) {
                costByProductSpec.putIfAbsent(
                        productPriceService.productSpecKey(productName, spec), row.getCostPrice());
            }
            if (row.getSupplyPrice() != null) {
                supplyByProductSpecPlatform.put(
                        productPriceService.productSpecPlatformKey(productName, spec, platform),
                        row.getSupplyPrice());
            }
        }

        Map<String, OrderProductPriceRow> deduped = new LinkedHashMap<>();
        for (OrderProductKey key : keys) {
            String compositeKey = toCompositeKey(key);
            deduped.putIfAbsent(
                    compositeKey,
                    new OrderProductPriceRow(
                            key.platform(),
                            key.productName(),
                            key.spec(),
                            costByProductSpec.get(
                                    productPriceService.productSpecKey(
                                            key.productName(), key.spec())),
                            supplyByProductSpecPlatform.get(
                                    productPriceService.productSpecPlatformKey(
                                            key.productName(), key.spec(), key.platform()))));
        }
        return List.copyOf(deduped.values());
    }

    private OrderProductKey toOrderProductKey(Object[] row) {
        String platform =
                productPriceService.normalizePlatform(row[0] == null ? null : row[0].toString());
        String productName =
                productPriceService.normalizeProductName(
                        row[1] == null ? null : row[1].toString());
        String spec = productPriceService.normalizeSpec(row[2] == null ? null : row[2].toString());
        return new OrderProductKey(platform, productName, spec);
    }

    private String toCompositeKey(OrderProductKey key) {
        return productPriceService.buildOrderProductKey(
                key.platform(), key.productName(), key.spec());
    }
}
