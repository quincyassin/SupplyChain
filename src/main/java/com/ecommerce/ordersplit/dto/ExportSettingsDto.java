package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.ExportMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导出配置
 *
 * @author huangxinsong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportSettingsDto {

    private ExportMode mode;

    private String updatedAt;
}
