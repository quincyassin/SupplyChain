package com.ecommerce.ordersplit.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 归档请求：将指定发单日期区间内的订单搬至归档表（含起止日）
 *
 * @author huangxinsong
 */
@Getter
@Setter
@NoArgsConstructor
public class ArchiveImportOrdersRequest {

    private LocalDate startDate;

    private LocalDate endDate;
}
