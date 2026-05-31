package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量回单结果
 *
 * @author huangxinsong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchReceiptResponse {

  /** 成功更新的订单行数 */
  private int updatedCount;

  /** 解析到的有效录入行数 */
  private int parsedLineCount;

  /** 未匹配到订单的录入行数 */
  private int notFoundLineCount;

  /** 未匹配的系统单号（去重） */
  private List<String> notFoundSystemNos;

  /** 更新后的当日订单列表 */
  private SplitResultResponse orders;
}
