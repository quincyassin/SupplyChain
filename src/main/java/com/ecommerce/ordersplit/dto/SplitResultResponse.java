package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分单回单结果（按商家分组，同名商家跨平台合并）
 *
 * @author huangxinsong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplitResultResponse {

  /** 本次分单任务 ID（数据已写入 import_order） */
  private Long taskId;

  /** 分单日期标签 */
  private String issueDate;

  /** 符合筛选条件的总行数 */
  private int totalRows;

  /** 平台数量（去重） */
  private int platformCount;

  /** 商家数量（去重，跨平台同名合并为一个商家） */
  private int merchantCount;

  /** 按商家分组汇总（分页模式下 rows 为空，仅 rowCount 有效） */
  private List<MerchantSplitGroupDto> merchantGroups;

  /** 按平台分组汇总（用于平台 Tab） */
  private List<PlatformSummaryDto> platformSummaries;

  /** 表格行（前端分页展示） */
  private List<DailyTableRowDto> pageRows;

  /** 当前日期区间内订单总数（用于判断能否按商家分单） */
  private int splittableOrderCount;
}
