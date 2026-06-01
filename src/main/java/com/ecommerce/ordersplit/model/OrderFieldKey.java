package com.ecommerce.ordersplit.model;

/**
 * 订单 Excel 可映射字段
 *
 * @author huangxinsong
 */
public enum OrderFieldKey {
  ORDER_NO("orderNo", "订单编号", false),
  MERCHANT("merchant", "商家", true),
  PRODUCT_NAME("productName", "商品名称", true),
  SKU("sku", "规格", false),
  QUANTITY("quantity", "数量", false),
  UNIT_PRICE("unitPrice", "单价", false),
  AMOUNT("amount", "金额", false),
  RECEIVER("receiver", "收货人", false),
  ADDRESS("address", "收货人地址", false),
  PHONE("phone", "收货人电话", false),
  SHIPPING_FEE("shippingFee", "运费", false),
  REMARK("remark", "备注", false),
  AFTER_SALES_REMARK("afterSalesRemark", "售后原因", false),
  LOGISTICS_NO("logisticsNo", "物流单号", false),
  LOGISTICS_COMPANY("logisticsCompany", "物流公司", false);

  private final String code;
  private final String label;
  private final boolean required;

  OrderFieldKey(String code, String label, boolean required) {
    this.code = code;
    this.label = label;
    this.required = required;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public boolean isRequired() {
    return required;
  }

  public static OrderFieldKey fromCode(String code) {
    for (OrderFieldKey key : values()) {
      if (key.code.equals(code)) {
        return key;
      }
    }
    throw new IllegalArgumentException("未知字段: " + code);
  }
}
