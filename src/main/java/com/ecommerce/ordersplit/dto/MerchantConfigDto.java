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
}
