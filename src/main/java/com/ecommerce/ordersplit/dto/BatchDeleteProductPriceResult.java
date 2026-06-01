package com.ecommerce.ordersplit.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 批量删除商品价格结果
 *
 * @author huangxinsong
 */
@Data
@Builder
public class BatchDeleteProductPriceResult {

    private int deletedCount;
}
