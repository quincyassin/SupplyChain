package com.ecommerce.ordersplit.model;

/**
 * 导入订单回单状态
 *
 * @author huangxinsong
 */
public enum ImportOrderReceiptStatus {

  /** 未回单 */
  PENDING("未回单"),

  /** 已回单 */
  RECEIPTED("已回单");

  private final String label;

  ImportOrderReceiptStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
