package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.ExportMode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 全局待分单批量分单并导出结果
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class AssignMerchantResult {

    /** 本次成功分单条数（含未匹配仍保留待分单的订单） */
    private int assignedCount;

    /** 未匹配关键字、仍保留为「待分单」的条数 */
    private int skippedCount;

    /** 本次导出归档日期（点击分单当天，ISO 字符串） */
    private String exportDate;

    /** 本次处理的订单系统编号（浏览器下载 ZIP 时使用） */
    private List<String> processedSystemNos;

    /** 浏览器下载令牌（已预构建 ZIP，10 分钟内有效） */
    private String exportDownloadToken;

    /** 导出到桌面的 Excel 绝对路径（浏览器下载模式下为空） */
    private List<String> exportedFiles;

    /** 本次导出的 Excel 文件数 */
    private int exportedFileCount;

    /** 当前导出方式 */
    private ExportMode exportMode;

    /** 导出根目录（SERVER_DIRECTORY 模式下有效） */
    private String exportDirectory;

    /** 分单后列表视图订单 */
    private SplitResultResponse orders;
}
