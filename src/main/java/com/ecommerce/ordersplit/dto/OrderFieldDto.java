package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 可配置字段说明
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class OrderFieldDto {

  private String fieldKey;
  private String label;
  private boolean required;
  /** 表头智能匹配别名 */
  private List<String> aliases;
}
