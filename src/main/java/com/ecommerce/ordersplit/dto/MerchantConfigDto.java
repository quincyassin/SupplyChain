package com.ecommerce.ordersplit.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商家配置 DTO
 *
 * @author huangxinsong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantConfigDto {

  private Long id;
  private String name;
  private List<String> keywords;
  private LocalDateTime updatedAt;

  /** 保存后自动重分单：扫描的未定义/未匹配订单数（仅 create 响应有值） */
  private Integer reassignedScannedCount;

  /** 保存后自动重分单：成功匹配到商家的订单数（仅 create 响应有值） */
  private Integer reassignedMatchedCount;

  /** 保存后自动重分单：仍为未定义的订单数（仅 create 响应有值） */
  private Integer reassignedStillPendingCount;
}
