package com.ecommerce.ordersplit.dto;

import lombok.Data;

/**
 * 标记订单需售后
 *
 * @author huangxinsong
 */
@Data
public class MarkAfterSalesRequest {

    /** 售后原因备注 */
    private String remark;
}
