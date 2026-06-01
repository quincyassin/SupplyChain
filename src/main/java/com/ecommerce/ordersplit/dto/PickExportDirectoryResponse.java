package com.ecommerce.ordersplit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选择导出根目录结果
 *
 * @author huangxinsong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PickExportDirectoryResponse {

    /** 用户是否取消了选择 */
    private boolean cancelled;

    /** 选中的目录（绝对路径）；取消时为 null */
    private String directory;
}
