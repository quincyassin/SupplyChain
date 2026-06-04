package com.ecommerce.ordersplit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ecommerce.ordersplit.entity.ProductPrice;
import com.ecommerce.ordersplit.repository.ProductPriceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订单商品目录服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class OrderProductCatalogServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private Query nativeQuery;
    @Mock private ProductPriceService productPriceService;
    @Mock private ProductPriceRepository productPriceRepository;

    @InjectMocks private OrderProductCatalogService orderProductCatalogService;

    @Test
    void listDistinctProductsWithPrices_shouldMergeOrderKeysAndConfiguredPrices() {
        when(entityManager.createNativeQuery(any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList())
                .thenReturn(Collections.singletonList(new Object[] {"淘宝", "商品A", "规格1"}));

        when(productPriceService.normalizePlatform("淘宝")).thenReturn("淘宝");
        when(productPriceService.normalizeProductName("商品A")).thenReturn("商品A");
        when(productPriceService.normalizeSpec("规格1")).thenReturn("规格1");
        when(productPriceService.buildOrderProductKey("淘宝", "商品A", "规格1"))
                .thenReturn("商品A\u0001规格1\u0001淘宝");
        when(productPriceService.productSpecKey("商品A", "规格1")).thenReturn("商品A\u0001规格1");
        when(productPriceService.productSpecPlatformKey("商品A", "规格1", "淘宝"))
                .thenReturn("商品A\u0001规格1\u0001淘宝");

        ProductPrice configured = new ProductPrice();
        configured.setPlatform("淘宝");
        configured.setProductName("商品A");
        configured.setSpec("规格1");
        configured.setCostPrice(new BigDecimal("10"));
        configured.setSupplyPrice(new BigDecimal("15"));
        when(productPriceRepository.findByProductNameIn(any())).thenReturn(List.of(configured));
        when(productPriceService.normalizeProductName(configured.getProductName()))
                .thenReturn("商品A");
        when(productPriceService.normalizeSpec(configured.getSpec())).thenReturn("规格1");
        when(productPriceService.normalizePlatform(configured.getPlatform())).thenReturn("淘宝");

        var rows = orderProductCatalogService.listDistinctProductsWithPrices(null);

        assertEquals(1, rows.size());
        assertEquals("商品A", rows.get(0).productName());
        assertEquals(new BigDecimal("10"), rows.get(0).costPrice());
        assertEquals(new BigDecimal("15"), rows.get(0).supplyPrice());
    }

    @Test
    void loadAllOrderProductKeys_shouldCollectDistinctKeys() {
        when(entityManager.createNativeQuery(any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList())
                .thenReturn(Collections.singletonList(new Object[] {"淘宝", "商品A", "规格1"}));
        when(productPriceService.normalizePlatform("淘宝")).thenReturn("淘宝");
        when(productPriceService.normalizeProductName("商品A")).thenReturn("商品A");
        when(productPriceService.normalizeSpec("规格1")).thenReturn("规格1");
        when(productPriceService.buildOrderProductKey("淘宝", "商品A", "规格1"))
                .thenReturn("key-a");

        var keys = orderProductCatalogService.loadAllOrderProductKeys();

        assertEquals(1, keys.size());
        assertTrue(keys.contains("key-a"));
    }

    @Test
    void listDistinctProducts_shouldUseFilteredSqlWhenKeywordPresent() {
        when(entityManager.createNativeQuery(any())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("productNamePattern"), eq("%kw%"))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("platformPattern"), eq("%kw%"))).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());

        var keys = orderProductCatalogService.listDistinctProducts("%kw%");

        assertEquals(0, keys.size());
    }
}
