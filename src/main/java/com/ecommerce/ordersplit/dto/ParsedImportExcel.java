package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.OrderRow;
import java.util.List;

/**
 * 单次 Excel 解析结果（表头 + 平台 + 数据行）
 *
 * @author huangxinsong
 */
public record ParsedImportExcel(
        List<ExcelHeaderDto> headers, String platform, List<OrderRow> rows) {}
