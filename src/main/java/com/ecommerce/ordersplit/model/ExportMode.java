package com.ecommerce.ordersplit.model;

/**
 * 分单后 Excel 导出方式
 *
 * @author huangxinsong
 */
public enum ExportMode {

    /** 写入服务器桌面 testData 目录（当前默认行为） */
    SERVER_DIRECTORY,

    /** 通过浏览器下载 ZIP 压缩包 */
    BROWSER_DOWNLOAD
}
