package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.ExportMode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 按商家回单导出结果
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class ReceiptExportResponse {

    /** 导出文件数 */
    private int exportedFileCount;

    /** 导出到桌面的 Excel 绝对路径（浏览器下载模式为空） */
    private List<String> exportedFiles;

    /** 当前导出方式 */
    private ExportMode exportMode;

    /** 浏览器下载令牌（已预构建 ZIP，10 分钟内有效） */
    private String exportDownloadToken;

    /** 本次导出写入的操作日（testData/{exportDate}/回单/） */
    private String exportDate;
}
