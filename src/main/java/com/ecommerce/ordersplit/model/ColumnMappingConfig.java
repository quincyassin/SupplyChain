package com.ecommerce.ordersplit.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Data;

/**
 * 列映射配置
 *
 * @author huangxinsong
 */
@Data
public class ColumnMappingConfig {

  private List<ColumnMappingItem> items = new ArrayList<>();

  public List<ColumnMappingItem> enabledItemsSorted() {
    return items.stream()
        .filter(ColumnMappingItem::isEnabled)
        .sorted(Comparator.comparingInt(ColumnMappingItem::getSortOrder))
        .toList();
  }
}
