package com.ecommerce.ordersplit.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 恢复请求：将归档表中指定发单日期区间的订单搬回主表
 *
 * @author huangxinsong
 */
@Getter
@Setter
@NoArgsConstructor
public class RestoreImportOrdersRequest {

    private LocalDate startDate;

    private LocalDate endDate;
}
