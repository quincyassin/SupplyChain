package com.ecommerce.ordersplit.model;

import lombok.Data;

/**
 * 列映射项
 *
 * @author huangxinsong
 */
@Data
public class ColumnMappingItem {

  private OrderFieldKey fieldKey;
  private int sourceIndex;
  private boolean enabled;
  private int sortOrder;
}
