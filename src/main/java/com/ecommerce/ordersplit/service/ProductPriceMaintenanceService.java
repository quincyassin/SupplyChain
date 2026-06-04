package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.BatchDeleteProductPriceRequest;
import com.ecommerce.ordersplit.dto.BatchDeleteProductPriceResult;
import com.ecommerce.ordersplit.dto.ProductPriceItemDto;
import com.ecommerce.ordersplit.dto.SaveProductPriceRequest;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ProductPriceRepository;
import com.ecommerce.ordersplit.util.SqlLikeUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品价格维护查询与保存
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ProductPriceMaintenanceService {

    private static final int KEYWORD_MAX_LENGTH = 100;

    private final ProductPriceService productPriceService;
    private final ProductPriceRepository productPriceRepository;
    private final OrderProductCatalogService orderProductCatalogService;

    @Transactional(readOnly = true)
    public List<ProductPriceItemDto> listProductPrices(String keyword) {
        return orderProductCatalogService
                .listDistinctProductsWithPrices(toContainsPattern(keyword))
                .stream()
                .map(this::toItemDto)
                .toList();
    }

    @Transactional
    public ProductPriceItemDto saveProductPrice(SaveProductPriceRequest request) {
        if (request == null) {
            throw new BusinessException("请求参数无效");
        }
        String productName = normalizeRequiredText(request.getProductName(), "商品名称");
        String spec = normalizeOptionalText(request.getSpec());
        String platform = normalizeOptionalText(request.getPlatform());
        if (request.getCostPrice() == null && request.getSupplyPrice() == null) {
            throw new BusinessException("请至少填写成本价或供货价");
        }

        productPriceService.saveMaintenancePrices(
                productName,
                spec,
                platform,
                request.getCostPrice(),
                request.getSupplyPrice());

        return ProductPriceItemDto.builder()
                .platform(platform)
                .productName(productName)
                .spec(spec)
                .costPrice(request.getCostPrice())
                .supplyPrice(request.getSupplyPrice())
                .build();
    }

    @Transactional
    public BatchDeleteProductPriceResult batchDeleteProductPrices(
            BatchDeleteProductPriceRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        int deletedCount = 0;
        for (ProductPriceItemDto item : request.getItems()) {
            deletedCount += deleteProductPriceItem(item);
        }
        if (deletedCount == 0) {
            throw new BusinessException("未删除任何记录，请刷新后重试");
        }
        return BatchDeleteProductPriceResult.builder().deletedCount(deletedCount).build();
    }

    private int deleteProductPriceItem(ProductPriceItemDto item) {
        if (item == null) {
            return 0;
        }
        String productName = normalizeRequiredText(item.getProductName(), "商品名称");
        String spec = normalizeOptionalText(item.getSpec());
        String platform = normalizeOptionalText(item.getPlatform());
        return productPriceRepository.deleteByPlatformAndProductNameAndSpec(
                platform, productName, spec);
    }

    private ProductPriceItemDto toItemDto(OrderProductCatalogService.OrderProductPriceRow row) {
        return ProductPriceItemDto.builder()
                .platform(row.platform())
                .productName(row.productName())
                .spec(row.spec())
                .costPrice(row.costPrice())
                .supplyPrice(row.supplyPrice())
                .build();
    }

    private String toContainsPattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException("搜索关键字不能超过 " + KEYWORD_MAX_LENGTH + " 个字符");
        }
        return SqlLikeUtil.toContainsPattern(trimmed);
    }

    private String normalizeRequiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(label + "不能为空");
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
