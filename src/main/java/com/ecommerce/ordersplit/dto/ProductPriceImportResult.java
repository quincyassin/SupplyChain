package com.ecommerce.ordersplit.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 商品价格 Excel 导入结果
 *
 * @author huangxinsong
 */
@Data
@Builder
public class ProductPriceImportResult {

    private int importedCount;

    private int skippedCount;

    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
