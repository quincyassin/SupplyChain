package com.ecommerce.ordersplit.model;

/**
 * 导入订单售后状态
 *
 * @author huangxinsong
 */
public enum AfterSalesStatus {

    /** 无需售后（含已取消售后） */
    NONE("无需售后"),

    /** 需售后，处理中 */
    PENDING("需售后"),

    /** 售后已完结 */
    COMPLETED("售后完结");

    private final String label;

    AfterSalesStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
