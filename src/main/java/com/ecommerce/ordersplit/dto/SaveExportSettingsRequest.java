package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.ExportMode;
import lombok.Data;

/**
 * 保存导出配置请求
 *
 * @author huangxinsong
 */
@Data
public class SaveExportSettingsRequest {

    private ExportMode mode;
}
