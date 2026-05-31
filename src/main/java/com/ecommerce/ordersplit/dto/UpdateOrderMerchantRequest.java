package com.ecommerce.ordersplit.dto;

import lombok.Data;

/**
 * 手动修改订单商家（不写入商家配置）
 *
 * @author huangxinsong
 */
@Data
public class UpdateOrderMerchantRequest {

  /** 商家名称 */
  private String merchant;
}
