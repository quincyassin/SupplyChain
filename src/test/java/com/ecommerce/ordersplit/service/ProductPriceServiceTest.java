package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ProductCostPrice;
import com.ecommerce.ordersplit.entity.ProductSupplyPrice;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.repository.ProductCostPriceRepository;
import com.ecommerce.ordersplit.repository.ProductSupplyPriceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.ecommerce.ordersplit.service.ProductPriceService.ImportPriceLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品价格服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ProductPriceServiceTest {

    @Mock
    private ProductCostPriceRepository productCostPriceRepository;

    @Mock
    private ProductSupplyPriceRepository productSupplyPriceRepository;

    @Mock
    private ImportOrderRepository importOrderRepository;

    private ProductPriceService productPriceService;

    @BeforeEach
    void setUp() {
        productPriceService =
                new ProductPriceService(
                        productCostPriceRepository,
                        productSupplyPriceRepository,
                        importOrderRepository);
    }

    @Test
    void saveCostPriceAndPropagate_shouldUpsertConfigAndUpdateOrders() {
        ImportOrder order = sampleOrder("商品A", "规格1", "淘宝");
        when(importOrderRepository.updateCostPriceByProductKey(
                        eq("商品A"), eq("规格1"), eq(new BigDecimal("18.50"))))
                .thenReturn(3);

        int updated =
                productPriceService.saveCostPriceAndPropagate(order, new BigDecimal("18.5"));

        assertEquals(3, updated);
        verify(productCostPriceRepository)
                .upsertCostPrice("商品A", "规格1", new BigDecimal("18.50"));
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
        verify(productSupplyPriceRepository)
                .upsertSupplyPrice("商品A", "规格1", "淘宝", new BigDecimal("22.00"));
    }

    @Test
    void applyConfiguredPrices_shouldFillFromConfigTables() {
        ImportOrder order = sampleOrder("商品A", "规格1", "淘宝");
        when(productCostPriceRepository.findByProductNameAndSpec("商品A", "规格1"))
                .thenReturn(
                        Optional.of(configuredCost("商品A", "规格1", new BigDecimal("10.00"))));
        when(productSupplyPriceRepository.findByProductNameAndSpecAndPlatform(
                        "商品A", "规格1", "淘宝"))
                .thenReturn(
                        Optional.of(
                                configuredSupply(
                                        "商品A", "规格1", "淘宝", new BigDecimal("12.00"))));

        productPriceService.applyConfiguredPrices(order);

        assertEquals(new BigDecimal("10.00"), order.getCostPrice());
        assertEquals(new BigDecimal("12.00"), order.getSupplyPrice());
    }

    @Test
    void buildLookupForImport_shouldBatchResolvePrices() {
        ImportOrder first = sampleOrder("商品A", "规格1", "淘宝");
        ImportOrder second = sampleOrder("商品B", "规格2", "京东");
        when(productCostPriceRepository.findByProductNameIn(any()))
                .thenReturn(
                        List.of(
                                configuredCost("商品A", "规格1", new BigDecimal("10.00")),
                                configuredCost("商品B", "规格2", new BigDecimal("20.00"))));
        when(productSupplyPriceRepository.findByProductNameIn(any()))
                .thenReturn(
                        List.of(
                                configuredSupply(
                                        "商品A", "规格1", "淘宝", new BigDecimal("12.00"))));

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
    void saveCostPriceAndPropagate_shouldRejectEmptyProductName() {
        ImportOrder order = sampleOrder("  ", "规格1", "淘宝");
        assertThrows(
                BusinessException.class,
                () -> productPriceService.saveCostPriceAndPropagate(order, BigDecimal.ONE));
    }

    private ImportOrder sampleOrder(String productName, String spec, String platform) {
        ImportOrder order = new ImportOrder();
        order.setSystemNo("V1StGXR8Z5jdHi6B");
        order.setProductName(productName);
        order.setSpec(spec);
        order.setPlatform(platform);
        return order;
    }

    private ProductCostPrice configuredCost(String productName, String spec, BigDecimal price) {
        ProductCostPrice entity = new ProductCostPrice();
        entity.setProductName(productName);
        entity.setSpec(spec);
        entity.setCostPrice(price);
        return entity;
    }

    private ProductSupplyPrice configuredSupply(
            String productName, String spec, String platform, BigDecimal price) {
        ProductSupplyPrice entity = new ProductSupplyPrice();
        entity.setProductName(productName);
        entity.setSpec(spec);
        entity.setPlatform(platform);
        entity.setSupplyPrice(price);
        return entity;
    }
}
