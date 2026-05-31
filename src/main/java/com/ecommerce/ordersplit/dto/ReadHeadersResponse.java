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
  /** 自动匹配到的平台（发单页上传时由表头识别） */
  private String matchedPlatform;
}
