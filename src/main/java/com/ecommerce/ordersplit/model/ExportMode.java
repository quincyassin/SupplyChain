package com.ecommerce.ordersplit.model;

/**
 * 分单后 Excel 导出方式
 *
 * @author huangxinsong
 */
public enum ExportMode {

    /** 写入服务器本地配置的导出目录（默认桌面 testData） */
    SERVER_DIRECTORY,

    /** 通过浏览器下载 ZIP 压缩包 */
    BROWSER_DOWNLOAD
}
