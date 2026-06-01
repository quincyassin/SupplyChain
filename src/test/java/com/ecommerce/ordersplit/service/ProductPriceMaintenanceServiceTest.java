package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.BatchDeleteProductPriceRequest;
import com.ecommerce.ordersplit.dto.ProductPriceItemDto;
import com.ecommerce.ordersplit.dto.SaveProductPriceRequest;
import com.ecommerce.ordersplit.entity.ProductPrice;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ProductPriceRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品价格维护服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ProductPriceMaintenanceServiceTest {

    @Mock private ProductPriceService productPriceService;
    @Mock private ProductPriceRepository productPriceRepository;

    @InjectMocks private ProductPriceMaintenanceService productPriceMaintenanceService;

    @Test
    void saveProductPrice_shouldDelegateToProductPriceService() {
        SaveProductPriceRequest request = new SaveProductPriceRequest();
        request.setPlatform("淘宝");
        request.setProductName("商品A");
        request.setSpec("规格1");
        request.setCostPrice(new BigDecimal("10"));
        request.setSupplyPrice(new BigDecimal("15"));

        when(productPriceService.saveMaintenancePrices(
                        "商品A", "规格1", "淘宝", new BigDecimal("10"), new BigDecimal("15")))
                .thenReturn(new ProductPriceService.ProductPriceSaveResult(1, 2));

        var saved = productPriceMaintenanceService.saveProductPrice(request);

        assertEquals("淘宝", saved.getPlatform());
        assertEquals("商品A", saved.getProductName());
        verify(productPriceService)
                .saveMaintenancePrices(
                        "商品A", "规格1", "淘宝", new BigDecimal("10"), new BigDecimal("15"));
    }

    @Test
    void saveProductPrice_shouldAllowEmptyPlatform() {
        SaveProductPriceRequest request = new SaveProductPriceRequest();
        request.setProductName("商品A");
        request.setSpec("规格1");
        request.setCostPrice(new BigDecimal("10"));

        when(productPriceService.saveMaintenancePrices(
                        "商品A", "规格1", "", new BigDecimal("10"), null))
                .thenReturn(new ProductPriceService.ProductPriceSaveResult(1, 0));

        var saved = productPriceMaintenanceService.saveProductPrice(request);

        assertEquals("", saved.getPlatform());
        verify(productPriceService)
                .saveMaintenancePrices("商品A", "规格1", "", new BigDecimal("10"), null);
    }

    @Test
    void batchDeleteProductPrices_shouldDeleteByCompositeKey() {
        BatchDeleteProductPriceRequest request = new BatchDeleteProductPriceRequest();
        request.setItems(
                List.of(
                        ProductPriceItemDto.builder()
                                .platform("淘宝")
                                .productName("商品A")
                                .spec("规格1")
                                .build()));
        when(productPriceRepository.deleteByPlatformAndProductNameAndSpec(
                        "淘宝", "商品A", "规格1"))
                .thenReturn(1);

        var result = productPriceMaintenanceService.batchDeleteProductPrices(request);

        assertEquals(1, result.getDeletedCount());
    }

    @Test
    void listProductPrices_shouldMapEntities() {
        ProductPrice entity = new ProductPrice();
        entity.setPlatform("淘宝");
        entity.setProductName("商品A");
        entity.setSpec("规格1");
        entity.setCostPrice(new BigDecimal("10"));
        entity.setSupplyPrice(new BigDecimal("15"));
        when(productPriceRepository.search(isNull())).thenReturn(List.of(entity));

        var items = productPriceMaintenanceService.listProductPrices(null);

        assertEquals(1, items.size());
        assertEquals("商品A", items.get(0).getProductName());
    }
}
