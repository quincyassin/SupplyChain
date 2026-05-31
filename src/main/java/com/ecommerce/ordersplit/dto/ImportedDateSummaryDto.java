package com.ecommerce.ordersplit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按分单日期汇总（分单回单页左侧日期筛选）
 *
 * @author huangxinsong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportedDateSummaryDto {

  /** yyyy-MM-dd */
  private String date;

  private String label;

  private int rowCount;

  private boolean today;
}
