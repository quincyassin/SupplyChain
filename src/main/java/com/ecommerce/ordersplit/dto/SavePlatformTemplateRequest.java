package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.Data;

/**
 * 保存平台表头模板请求
 *
 * @author huangxinsong
 */
@Data
public class SavePlatformTemplateRequest {

  private List<ColumnMappingItemDto> mapping;
  private List<ExcelHeaderDto> templateHeaders;
  private String templateFileName;
}
