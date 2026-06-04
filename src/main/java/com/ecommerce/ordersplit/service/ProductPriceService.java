package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ProductPrice;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.repository.ProductPriceRepository;
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
 * 商品价格维护与订单同步
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ProductPriceService {

    private static final String KEY_SEPARATOR = "\u0001";

    private final ProductPriceRepository productPriceRepository;
    private final ImportOrderRepository importOrderRepository;

    public record ImportPriceLookup(
            Map<String, BigDecimal> costByProductSpec,
            Map<String, BigDecimal> supplyByProductSpecPlatform) {

        static ImportPriceLookup empty() {
            return new ImportPriceLookup(Map.of(), Map.of());
        }
    }

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

        List<ProductPrice> rows = productPriceRepository.findByProductNameIn(productNames);
        Map<String, BigDecimal> costMap = new HashMap<>();
        Map<String, BigDecimal> supplyMap = new HashMap<>();
        for (ProductPrice row : rows) {
            if (row.getCostPrice() != null) {
                costMap.putIfAbsent(
                        productSpecKey(row.getProductName(), row.getSpec()), row.getCostPrice());
            }
            if (row.getSupplyPrice() != null) {
                supplyMap.put(
                        productSpecPlatformKey(
                                row.getProductName(), row.getSpec(), row.getPlatform()),
                        row.getSupplyPrice());
            }
        }
        return new ImportPriceLookup(costMap, supplyMap);
    }

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

    @Transactional
    public int saveCostPriceAndPropagate(ImportOrder sourceOrder, BigDecimal costPrice) {
        ProductKey key = requireProductKey(sourceOrder);
        String platform = normalizePlatform(sourceOrder.getPlatform());
        if (platform.isEmpty()) {
            throw new BusinessException("订单缺少平台信息，无法维护成本价");
        }
        return saveCostPriceByKey(key.productName(), key.spec(), platform, costPrice);
    }

    @Transactional
    public int saveSupplyPriceAndPropagate(ImportOrder sourceOrder, BigDecimal supplyPrice) {
        ProductKey key = requireProductKey(sourceOrder);
        String platform = normalizePlatform(sourceOrder.getPlatform());
        if (platform.isEmpty()) {
            throw new BusinessException("订单缺少平台信息，无法维护供货价");
        }
        return saveSupplyPriceByKey(key.productName(), key.spec(), platform, supplyPrice);
    }

    @Transactional
    public int saveCostPriceByKey(
            String productName, String spec, String platform, BigDecimal costPrice) {
        String normalizedProductName = normalizeProductName(productName);
        if (normalizedProductName.isEmpty()) {
            throw new BusinessException("商品名称不能为空");
        }
        String normalizedSpec = normalizeSpec(spec);
        String normalizedPlatform = normalizePlatform(platform);
        BigDecimal normalizedPrice = normalizePrice(costPrice, "成本价");
        upsertPrice(normalizedPlatform, normalizedProductName, normalizedSpec, normalizedPrice, null);
        productPriceRepository.updateCostPriceByProductKey(
                normalizedProductName, normalizedSpec, normalizedPrice);
        return importOrderRepository.updateCostPriceByProductKey(
                normalizedProductName, normalizedSpec, normalizedPrice);
    }

    @Transactional
    public int saveSupplyPriceByKey(
            String productName, String spec, String platform, BigDecimal supplyPrice) {
        String normalizedProductName = normalizeProductName(productName);
        if (normalizedProductName.isEmpty()) {
            throw new BusinessException("商品名称不能为空");
        }
        String normalizedSpec = normalizeSpec(spec);
        String normalizedPlatform = normalizePlatform(platform);
        BigDecimal normalizedPrice = normalizePrice(supplyPrice, "供货价");
        upsertPrice(normalizedPlatform, normalizedProductName, normalizedSpec, null, normalizedPrice);
        return importOrderRepository.updateSupplyPriceByProductPlatformKey(
                normalizedProductName, normalizedSpec, normalizedPlatform, normalizedPrice);
    }

    @Transactional
    public ProductPriceSaveResult saveMaintenancePrices(
            String productName,
            String spec,
            String platform,
            BigDecimal costPrice,
            BigDecimal supplyPrice) {
        if (costPrice == null && supplyPrice == null) {
            throw new BusinessException("请至少填写成本价或供货价");
        }
        String normalizedProductName = normalizeProductName(productName);
        if (normalizedProductName.isEmpty()) {
            throw new BusinessException("商品名称不能为空");
        }
        String normalizedSpec = normalizeSpec(spec);
        String normalizedPlatform = normalizePlatform(platform);

        upsertPrice(
                normalizedPlatform,
                normalizedProductName,
                normalizedSpec,
                costPrice == null ? null : normalizePrice(costPrice, "成本价"),
                supplyPrice == null ? null : normalizePrice(supplyPrice, "供货价"));

        int costUpdated = 0;
        int supplyUpdated = 0;
        if (costPrice != null) {
            BigDecimal normalizedCost = normalizePrice(costPrice, "成本价");
            productPriceRepository.updateCostPriceByProductKey(
                    normalizedProductName, normalizedSpec, normalizedCost);
            costUpdated =
                    importOrderRepository.updateCostPriceByProductKey(
                            normalizedProductName, normalizedSpec, normalizedCost);
        }
        if (supplyPrice != null) {
            supplyUpdated =
                    importOrderRepository.updateSupplyPriceByProductPlatformKey(
                            normalizedProductName,
                            normalizedSpec,
                            normalizedPlatform,
                            normalizePrice(supplyPrice, "供货价"));
        }
        return new ProductPriceSaveResult(costUpdated, supplyUpdated);
    }

    @Transactional
    public ProductPriceSaveResult upsertImportedRow(
            String platform,
            String productName,
            String spec,
            BigDecimal costPrice,
            BigDecimal supplyPrice) {
        return saveMaintenancePrices(productName, spec, platform, costPrice, supplyPrice);
    }

    private Optional<BigDecimal> resolveCostPrice(String productName, String spec) {
        List<ProductPrice> rows = productPriceRepository.findByProductNameAndSpec(productName, spec);
        for (ProductPrice row : rows) {
            if (row.getCostPrice() != null) {
                return Optional.of(row.getCostPrice());
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> resolveSupplyPrice(
            String productName, String spec, String platform) {
        return productPriceRepository
                .findByPlatformAndProductNameAndSpec(platform, productName, spec)
                .map(ProductPrice::getSupplyPrice)
                .filter(price -> price != null);
    }

    private void upsertPrice(
            String platform,
            String productName,
            String spec,
            BigDecimal costPrice,
            BigDecimal supplyPrice) {
        productPriceRepository.upsert(platform, productName, spec, costPrice, supplyPrice);
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

    String productSpecKey(String productName, String spec) {
        return normalizeProductName(productName) + KEY_SEPARATOR + normalizeSpec(spec);
    }

    String productSpecPlatformKey(String productName, String spec, String platform) {
        return productSpecKey(productName, spec) + KEY_SEPARATOR + normalizePlatform(platform);
    }

    public String buildOrderProductKey(String platform, String productName, String spec) {
        return productSpecPlatformKey(productName, spec, platform);
    }

    public String normalizeProductName(String productName) {
        return productName == null ? "" : productName.trim();
    }

    public String normalizeSpec(String spec) {
        return spec == null ? "" : spec.trim();
    }

    public String normalizePlatform(String platform) {
        return platform == null ? "" : platform.trim();
    }

    private record ProductKey(String productName, String spec) {}

    public record ProductPriceSaveResult(int costUpdatedCount, int supplyUpdatedCount) {}
}
