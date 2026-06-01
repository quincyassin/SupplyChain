package com.ecommerce.ordersplit.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 商品价格维护列表项
 *
 * @author huangxinsong
 */
@Data
@Builder
public class ProductPriceItemDto {

    private String platform;

    private String productName;

    private String spec;

    private BigDecimal costPrice;

    private BigDecimal supplyPrice;
}
