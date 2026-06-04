package com.ecommerce.ordersplit.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Excel 订单行数据
 *
 * @author huangxinsong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRow {

  private String orderNo;
  private String merchant;
  private String productName;
  private String sku;
  private Integer quantity;
  private BigDecimal unitPrice;
  private BigDecimal amount;
  private String receiver;
  private String address;
  private String phone;
  /** 运费 */
  private BigDecimal shippingFee;
  /** 备注 */
  private String remark;
  /** 售后原因（Excel 导入映射） */
  private String afterSalesRemark;
  /** 物流单号（Excel 导入映射） */
  private String logisticsNo;
  /** 物流公司（Excel 导入映射） */
  private String logisticsCompany;
  /** 原始 Excel 行号，便于追溯 */
  private int sourceRowNum;
  /** 系统编号（持久化订单匹配用） */
  private String systemNo;
}
