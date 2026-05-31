package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当日表格生成结果
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class DailyTableResponse {

  /** 发单日期（本次生成时间） */
  private String issueDate;

  private int totalRows;

  private List<DailyTableRowDto> rows;
}
