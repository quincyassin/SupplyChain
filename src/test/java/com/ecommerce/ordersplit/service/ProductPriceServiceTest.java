package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ProductPrice;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.repository.ProductPriceRepository;
import com.ecommerce.ordersplit.service.ProductPriceService.ImportPriceLookup;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品价格服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ProductPriceServiceTest {

    @Mock private ProductPriceRepository productPriceRepository;
    @Mock private ImportOrderRepository importOrderRepository;

    private ProductPriceService productPriceService;

    @BeforeEach
    void setUp() {
        productPriceService = new ProductPriceService(productPriceRepository, importOrderRepository);
    }

    @Test
    void saveCostPriceAndPropagate_shouldUpsertAndSyncOrders() {
        ImportOrder order = sampleOrder("商品A", "规格1", "淘宝");
        when(importOrderRepository.updateCostPriceByProductKey(
                        eq("商品A"), eq("规格1"), eq(new BigDecimal("18.50"))))
                .thenReturn(3);

        int updated =
                productPriceService.saveCostPriceAndPropagate(order, new BigDecimal("18.5"));

        assertEquals(3, updated);
        verify(productPriceRepository)
                .upsert("淘宝", "商品A", "规格1", new BigDecimal("18.50"), null);
        verify(productPriceRepository)
                .updateCostPriceByProductKey("商品A", "规格1", new BigDecimal("18.50"));
    }

    @Test
    void saveSupplyPriceAndPropagate_shouldUsePlatformKey() {
        ImportOrder order = sampleOrder("商品A", "规格1", "淘宝");
        when(importOrderRepository.updateSupplyPriceByProductPlatformKey(
                        eq("商品A"), eq("规格1"), eq("淘宝"), eq(new BigDecimal("22.00"))))
                .thenReturn(2);

        int updated =
                productPriceService.saveSupplyPriceAndPropagate(order, new BigDecimal("22"));

        assertEquals(2, updated);
        verify(productPriceRepository)
                .upsert("淘宝", "商品A", "规格1", null, new BigDecimal("22.00"));
    }

    @Test
    void buildLookupForImport_shouldBatchResolvePrices() {
        ImportOrder first = sampleOrder("商品A", "规格1", "淘宝");
        ImportOrder second = sampleOrder("商品B", "规格2", "京东");
        when(productPriceRepository.findByProductNameIn(any()))
                .thenReturn(
                        List.of(
                                configuredPrice("商品A", "规格1", "淘宝", new BigDecimal("10.00"), new BigDecimal("12.00")),
                                configuredPrice("商品B", "规格2", "京东", new BigDecimal("20.00"), null)));

        ImportPriceLookup lookup =
                productPriceService.buildLookupForImport(List.of(first, second));

        productPriceService.applyConfiguredPrices(first, lookup);
        productPriceService.applyConfiguredPrices(second, lookup);

        assertEquals(new BigDecimal("10.00"), first.getCostPrice());
        assertEquals(new BigDecimal("12.00"), first.getSupplyPrice());
        assertEquals(new BigDecimal("20.00"), second.getCostPrice());
        assertEquals(null, second.getSupplyPrice());
    }

    @Test
    void saveMaintenancePrices_shouldRequireAtLeastOnePrice() {
        assertThrows(
                BusinessException.class,
                () ->
                        productPriceService.saveMaintenancePrices(
                                "商品A", "规格1", "淘宝", null, null));
    }

    @Test
    void applyConfiguredPrices_shouldFillFromUnifiedTable() {
        ImportOrder order = sampleOrder("商品A", "规格1", "淘宝");
        when(productPriceRepository.findByProductNameAndSpec("商品A", "规格1"))
                .thenReturn(
                        List.of(
                                configuredPrice(
                                        "商品A", "规格1", "淘宝", new BigDecimal("10.00"), new BigDecimal("12.00"))));
        when(productPriceRepository.findByPlatformAndProductNameAndSpec("淘宝", "商品A", "规格1"))
                .thenReturn(
                        Optional.of(
                                configuredPrice(
                                        "商品A", "规格1", "淘宝", new BigDecimal("10.00"), new BigDecimal("12.00"))));

        productPriceService.applyConfiguredPrices(order);

        assertEquals(new BigDecimal("10.00"), order.getCostPrice());
        assertEquals(new BigDecimal("12.00"), order.getSupplyPrice());
    }

    private ImportOrder sampleOrder(String productName, String spec, String platform) {
        ImportOrder order = new ImportOrder();
        order.setSystemNo("V1StGXR8Z5jdHi6B");
        order.setProductName(productName);
        order.setSpec(spec);
        order.setPlatform(platform);
        return order;
    }

    private ProductPrice configuredPrice(
            String productName,
            String spec,
            String platform,
            BigDecimal costPrice,
            BigDecimal supplyPrice) {
        ProductPrice entity = new ProductPrice();
        entity.setProductName(productName);
        entity.setSpec(spec);
        entity.setPlatform(platform);
        entity.setCostPrice(costPrice);
        entity.setSupplyPrice(supplyPrice);
        return entity;
    }
}
