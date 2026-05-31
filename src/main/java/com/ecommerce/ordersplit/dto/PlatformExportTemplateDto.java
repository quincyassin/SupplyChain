package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 按平台模板导出 Excel 所需的映射与表头
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class PlatformExportTemplateDto {

    private String platform;

    private ColumnMappingConfig mapping;

    private List<ExcelHeaderDto> templateHeaders;
}
