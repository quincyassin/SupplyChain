package com.ecommerce.ordersplit.dto;

import lombok.Data;

/**
 * 列映射项（字段挑选 + 排序）
 *
 * @author huangxinsong
 */
@Data
public class ColumnMappingItemDto {

  /** 系统字段 key，如 merchant */
  private String fieldKey;

  /** 对应 Excel 列下标（从 0 开始） */
  private Integer sourceIndex;

  /** 是否参与导入/导出 */
  private Boolean enabled;

  /** 输出排序（越小越靠前） */
  private Integer sortOrder;
}
