package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.Data;

/**
 * 选中行导出 / 批量删除请求
 *
 * @author huangxinsong
 */
@Data
public class ExportSelectedRequest {

    /** 选中的 import_order.system_no 列表 */
    private List<String> systemNos;
}
