package com.ecommerce.ordersplit.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台模板详情
 *
 * @author huangxinsong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTemplateDetailDto {

  private String platform;
  private String templateFileName;
  private List<ColumnMappingItemDto> mapping;
  private List<ExcelHeaderDto> templateHeaders;
  private LocalDateTime updatedAt;
}
