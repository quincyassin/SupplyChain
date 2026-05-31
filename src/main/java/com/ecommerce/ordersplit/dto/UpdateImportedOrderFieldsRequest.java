package com.ecommerce.ordersplit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 手动修改订单可编辑字段
 *
 * @author huangxinsong
 */
@Data
public class UpdateImportedOrderFieldsRequest {

    /** 订单编号 */
    private String orderNo;

    /** 物流单号 */
    private String logisticsNo;

    /** 物流公司 */
    private String logisticsCompany;

    /** 收货人 */
    private String receiver;

    /** 收货人电话 */
    private String phone;

    /** 收货人地址 */
    private String address;

    /** 运费 */
    private BigDecimal shippingFee;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 供货价 */
    private BigDecimal supplyPrice;

    /** 备注 */
    private String remark;
}
