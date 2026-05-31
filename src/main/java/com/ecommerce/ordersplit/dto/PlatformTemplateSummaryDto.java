package com.ecommerce.ordersplit.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 平台模板列表项
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class PlatformTemplateSummaryDto {

  private String platform;
  private String templateFileName;
  private LocalDateTime updatedAt;
}
