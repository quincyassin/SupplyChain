package com.ecommerce.ordersplit.dto;

import java.time.LocalDate;
import lombok.Data;

/**
 * 售后订单导出请求
 *
 * @author huangxinsong
 */
@Data
public class AfterSalesExportRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    /** 关键字（商家、平台、系统编号、物流单号、订单编号） */
    private String keyword;
}
