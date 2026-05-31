package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.Data;

/**
 * 保存商家配置请求
 *
 * @author huangxinsong
 */
@Data
public class SaveMerchantConfigRequest {

  private String name;
  private List<String> keywords;
}
