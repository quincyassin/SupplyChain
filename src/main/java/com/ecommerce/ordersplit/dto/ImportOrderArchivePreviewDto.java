package com.ecommerce.ordersplit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 归档前预览
 *
 * @author huangxinsong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportOrderArchivePreviewDto {

    /** 操作涉及的日期区间描述，如 2024-01-01 ~ 2024-06-30 */
    private String beforeDate;

    /** 将被归档的订单总数 */
    private long orderCount;

    /** 其中「需售后」未完结条数 */
    private long pendingAfterSalesCount;

    /** 其中「售后完结」条数 */
    private long completedAfterSalesCount;
}
