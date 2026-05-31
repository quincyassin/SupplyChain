package com.ecommerce.ordersplit.dto;

import java.time.LocalDate;
import lombok.Data;

/**
 * 对账导出请求
 *
 * @author huangxinsong
 */
@Data
public class ReconcileExportRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    /** 商家对账：目标商家名称 */
    private String merchant;

    /** 平台对账：目标平台名称 */
    private String platform;
}
