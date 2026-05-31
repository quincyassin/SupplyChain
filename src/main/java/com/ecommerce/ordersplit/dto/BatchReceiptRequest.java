package com.ecommerce.ordersplit.dto;

import lombok.Data;

/**
 * 批量回单请求（文本框多行录入）
 *
 * @author huangxinsong
 */
@Data
public class BatchReceiptRequest {

  /** 多行文本：系统单号、物流单号、物流公司（制表符/逗号/空格分隔） */
  private String content;
}
