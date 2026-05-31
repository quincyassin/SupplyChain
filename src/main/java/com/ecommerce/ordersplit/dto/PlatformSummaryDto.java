package com.ecommerce.ordersplit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 平台订单汇总（用于 Tab 展示）
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class PlatformSummaryDto {

    /** 平台名称 */
    private String platform;

    /** 订单行数 */
    private int rowCount;

    /** 已回单订单行数 */
    private int receiptedCount;
}
