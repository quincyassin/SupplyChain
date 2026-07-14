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

  /** 保存后重匹配近一周订单：扫描条数（create / update 响应有值） */
  private Integer reassignedScannedCount;

  /** 保存后重匹配近一周订单：成功匹配到商家的条数（create / update 响应有值） */
  private Integer reassignedMatchedCount;

  /** 保存后重匹配近一周订单：仍为未定义的条数（create / update 响应有值） */
  private Integer reassignedStillPendingCount;
}
