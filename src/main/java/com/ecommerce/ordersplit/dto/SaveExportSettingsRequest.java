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

    /** 导出根目录（绝对路径，SERVER_DIRECTORY 模式必填） */
    private String exportDirectory;
}
