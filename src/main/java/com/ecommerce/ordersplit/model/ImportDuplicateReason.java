package com.ecommerce.ordersplit.model;

/**
 * 导入订单编号重复原因
 *
 * @author huangxinsong
 */
public enum ImportDuplicateReason {
    /** Excel 文件内重复（非首次出现） */
    FILE,
    /** 历史订单中已存在（含归档） */
    DATABASE
}
