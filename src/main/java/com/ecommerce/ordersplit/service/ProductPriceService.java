package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ProductCostPrice;
import com.ecommerce.ordersplit.entity.ProductSupplyPrice;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.repository.ProductCostPriceRepository;
import com.ecommerce.ordersplit.repository.ProductSupplyPriceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品成本价 / 供货价维护与订单同步
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ProductPriceService {

    private static final String KEY_SEPARATOR = "\u0001";

    private final ProductCostPriceRepository productCostPriceRepository;
    private final ProductSupplyPriceRepository productSupplyPriceRepository;
    private final ImportOrderRepository importOrderRepository;

    /**
     * 导入批量查价索引（一次查询成本价 / 供货价配置表）
     */
    public record ImportPriceLookup(
            Map<String, BigDecimal> costByProductSpec,
            Map<String, BigDecimal> supplyByProductSpecPlatform) {

        static ImportPriceLookup empty() {
            return new ImportPriceLookup(Map.of(), Map.of());
        }
    }

    /**
     * 为批量导入构建查价索引
     */
    @Transactional(readOnly = true)
    public ImportPriceLookup buildLookupForImport(Collection<ImportOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return ImportPriceLookup.empty();
        }
        Set<String> productNames = new LinkedHashSet<>();
        for (ImportOrder order : orders) {
            String productName = normalizeProductName(order.getProductName());
            if (!productName.isEmpty()) {
                productNames.add(productName);
            }
        }
        if (productNames.isEmpty()) {
            return ImportPriceLookup.empty();
        }

        List<ProductCostPrice> costRows =
                productCostPriceRepository.findByProductNameIn(productNames);
        List<ProductSupplyPrice> supplyRows =
                productSupplyPriceRepository.findByProductNameIn(productNames);

        Map<String, BigDecimal> costMap = new HashMap<>();
        for (ProductCostPrice row : costRows) {
            costMap.put(
                    productSpecKey(row.getProductName(), row.getSpec()), row.getCostPrice());
        }

        Map<String, BigDecimal> supplyMap = new HashMap<>();
        for (ProductSupplyPrice row : supplyRows) {
            supplyMap.put(
                    productSpecPlatformKey(
                            row.getProductName(), row.getSpec(), row.getPlatform()),
                    row.getSupplyPrice());
        }
        return new ImportPriceLookup(costMap, supplyMap);
    }

    /**
     * 从配置表回填订单价格（导入时使用）
     */
    @Transactional(readOnly = true)
    public void applyConfiguredPrices(ImportOrder order) {
        if (order == null) {
            return;
        }
        String productName = normalizeProductName(order.getProductName());
        if (productName.isEmpty()) {
            return;
        }
        String spec = normalizeSpec(order.getSpec());
        resolveCostPrice(productName, spec).ifPresent(order::setCostPrice);

        String platform = normalizePlatform(order.getPlatform());
        resolveSupplyPrice(productName, spec, platform).ifPresent(order::setSupplyPrice);
    }

    /**
     * 使用批量查价索引回填订单价格（导入批量写入时使用）
     */
    public void applyConfiguredPrices(ImportOrder order, ImportPriceLookup lookup) {
        if (order == null || lookup == null) {
            return;
        }
        String productName = normalizeProductName(order.getProductName());
        if (productName.isEmpty()) {
            return;
        }
        String spec = normalizeSpec(order.getSpec());
        BigDecimal cost = lookup.costByProductSpec().get(productSpecKey(productName, spec));
        if (cost != null) {
            order.setCostPrice(cost);
        }

        String platform = normalizePlatform(order.getPlatform());
        BigDecimal supply =
                lookup.supplyByProductSpecPlatform()
                        .get(productSpecPlatformKey(productName, spec, platform));
        if (supply != null) {
            order.setSupplyPrice(supply);
        }
    }

    /**
     * 维护成本价并同步所有相同「商品名称 + 规格」的订单
     */
    @Transactional
    public int saveCostPriceAndPropagate(ImportOrder sourceOrder, BigDecimal costPrice) {
        ProductKey key = requireProductKey(sourceOrder);
        BigDecimal normalizedPrice = normalizePrice(costPrice, "成本价");
        upsertCostPrice(key.productName(), key.spec(), normalizedPrice);
        return importOrderRepository.updateCostPriceByProductKey(
                key.productName(), key.spec(), normalizedPrice);
    }

    /**
     * 维护供货价并同步所有相同「商品名称 + 规格 + 平台」的订单
     */
    @Transactional
    public int saveSupplyPriceAndPropagate(ImportOrder sourceOrder, BigDecimal supplyPrice) {
        ProductKey key = requireProductKey(sourceOrder);
        String platform = normalizePlatform(sourceOrder.getPlatform());
        if (platform.isEmpty()) {
            throw new BusinessException("订单缺少平台信息，无法维护供货价");
        }
        BigDecimal normalizedPrice = normalizePrice(supplyPrice, "供货价");
        upsertSupplyPrice(key.productName(), key.spec(), platform, normalizedPrice);
        return importOrderRepository.updateSupplyPriceByProductPlatformKey(
                key.productName(), key.spec(), platform, normalizedPrice);
    }

    private Optional<BigDecimal> resolveCostPrice(String productName, String spec) {
        return productCostPriceRepository
                .findByProductNameAndSpec(productName, spec)
                .map(ProductCostPrice::getCostPrice);
    }

    private Optional<BigDecimal> resolveSupplyPrice(String productName, String spec, String platform) {
        return productSupplyPriceRepository
                .findByProductNameAndSpecAndPlatform(productName, spec, platform)
                .map(ProductSupplyPrice::getSupplyPrice);
    }

    private void upsertCostPrice(String productName, String spec, BigDecimal costPrice) {
        productCostPriceRepository.upsertCostPrice(productName, spec, costPrice);
    }

    private void upsertSupplyPrice(
            String productName, String spec, String platform, BigDecimal supplyPrice) {
        productSupplyPriceRepository.upsertSupplyPrice(
                productName, spec, platform, supplyPrice);
    }

    private ProductKey requireProductKey(ImportOrder order) {
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        String productName = normalizeProductName(order.getProductName());
        if (productName.isEmpty()) {
            throw new BusinessException("商品名称为空，无法维护价格");
        }
        return new ProductKey(productName, normalizeSpec(order.getSpec()));
    }

    private BigDecimal normalizePrice(BigDecimal price, String label) {
        if (price == null) {
            throw new BusinessException(label + "不能为空");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(label + "不能为负数");
        }
        if (price.precision() - price.scale() > 10) {
            throw new BusinessException(label + "整数部分不能超过 10 位");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String productSpecKey(String productName, String spec) {
        return normalizeProductName(productName) + KEY_SEPARATOR + normalizeSpec(spec);
    }

    private String productSpecPlatformKey(String productName, String spec, String platform) {
        return productSpecKey(productName, spec) + KEY_SEPARATOR + normalizePlatform(platform);
    }

    private String normalizeProductName(String productName) {
        return productName == null ? "" : productName.trim();
    }

    private String normalizeSpec(String spec) {
        return spec == null ? "" : spec.trim();
    }

    private String normalizePlatform(String platform) {
        return platform == null ? "" : platform.trim();
    }

    private record ProductKey(String productName, String spec) {}
}
