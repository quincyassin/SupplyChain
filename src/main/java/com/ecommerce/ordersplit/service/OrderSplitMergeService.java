package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.model.OrderRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 分单合单：按商家分组，保留 Excel 原始订单编号（system_no 为系统内主键）
 *
 * @author huangxinsong
 */
@Service
public class OrderSplitMergeService {

  /** 按商家分组，不修改订单编号等业务字段 */
  public Map<String, List<OrderRow>> groupByMerchant(List<OrderRow> rows) {
    return rows.stream()
        .collect(Collectors.groupingBy(OrderRow::getMerchant, LinkedHashMap::new, Collectors.toList()));
  }
}
