package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 按平台分组的分单数据（其下再按商家分组）
 *
 * @author huangxinsong
 */
@Data
@AllArgsConstructor
public class PlatformSplitGroupDto {

  /** 平台名称（导入时匹配的平台模板） */
  private String platform;

  /** 该平台订单行数 */
  private int rowCount;

  /** 该平台商家数量 */
  private int merchantCount;

  /** 按商家分组 */
  private List<MerchantSplitGroupDto> merchantGroups;
}
