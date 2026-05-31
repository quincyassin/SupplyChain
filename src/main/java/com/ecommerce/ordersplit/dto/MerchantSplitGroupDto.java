package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 按商家分组的分单数据
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class MerchantSplitGroupDto {

  /** 商家名称 */
  private String merchant;

  /** 该商家订单行数 */
  private int rowCount;

  /** 已回单订单行数 */
  private int receiptedCount;

  /** 发单表格行 */
  private List<DailyTableRowDto> rows;
}
