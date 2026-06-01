package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.Data;

/**
 * 批量删除商品价格请求
 *
 * @author huangxinsong
 */
@Data
public class BatchDeleteProductPriceRequest {

    private List<ProductPriceItemDto> items;
}
