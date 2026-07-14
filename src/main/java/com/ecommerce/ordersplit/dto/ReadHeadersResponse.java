package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 读取 Excel 表头响应
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class ReadHeadersResponse {

  private List<ExcelHeaderDto> headers;
  private List<ColumnMappingItemDto> suggestedMapping;
  private List<OrderFieldDto> fields;
  /** 自动匹配到的平台（唯一匹配时有值；歧义时为 null） */
  private String matchedPlatform;
  /** 表头命中的候选平台（歧义时供用户选择） */
  private List<String> candidatePlatforms;
  /** 是否存在多平台同分歧义，需用户手动选择 */
  private boolean platformAmbiguous;
}
