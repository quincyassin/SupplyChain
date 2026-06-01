package com.ecommerce.ordersplit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保存商品价格维护请求
 *
 * @author huangxinsong
 */
@Data
public class SaveProductPriceRequest {

    private String platform;

    private String productName;

    private String spec;

    private BigDecimal costPrice;

    private BigDecimal supplyPrice;
}
