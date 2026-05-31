package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.model.OrderRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 分单合单核心业务
 *
 * @author huangxinsong
 */
@Service
public class OrderSplitMergeService {

  private static final DateTimeFormatter ORDER_NO_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  /**
   * 分单：按商家分组，每个商家保留原始明细行，生成新子订单号
   */
  public Map<String, List<OrderRow>> splitByMerchant(List<OrderRow> inputRows) {
    Map<String, List<OrderRow>> grouped = groupByMerchant(inputRows);
    Map<String, List<OrderRow>> result = new LinkedHashMap<>();
    int merchantIndex = 1;

    for (Map.Entry<String, List<OrderRow>> entry : grouped.entrySet()) {
      String merchant = entry.getKey();
      String batchPrefix = "SPLIT-" + ORDER_NO_FORMAT.format(LocalDateTime.now()) + "-" + merchantIndex;
      List<OrderRow> splitRows = new ArrayList<>();
      int lineIndex = 1;

      for (OrderRow original : entry.getValue()) {
        OrderRow splitRow = copyRow(original);
        splitRow.setOrderNo(batchPrefix + "-" + String.format("%03d", lineIndex++));
        splitRows.add(splitRow);
      }
      result.put(merchant, splitRows);
      merchantIndex++;
    }
    return result;
  }

  /**
   * 合单：按商家 + SKU 合并数量与金额，每个商家生成一张合并订单
   */
  public List<OrderRow> mergeByMerchant(List<OrderRow> inputRows) {
    Map<String, List<OrderRow>> grouped = groupByMerchant(inputRows);
    List<OrderRow> merged = new ArrayList<>();
    int merchantIndex = 1;

    for (Map.Entry<String, List<OrderRow>> entry : grouped.entrySet()) {
      String merchant = entry.getKey();
      String mergedOrderNo =
          "MERGE-" + ORDER_NO_FORMAT.format(LocalDateTime.now()) + "-" + merchantIndex;

      Map<String, List<OrderRow>> skuGroups =
          entry.getValue().stream()
              .collect(
                  Collectors.groupingBy(
                      row -> row.getSku() == null || row.getSku().isBlank() ? row.getProductName() : row.getSku(),
                      LinkedHashMap::new,
                      Collectors.toList()));

      int lineIndex = 1;
      for (List<OrderRow> skuRows : skuGroups.values()) {
        OrderRow first = skuRows.get(0);
        int totalQty = skuRows.stream().mapToInt(r -> r.getQuantity() == null ? 0 : r.getQuantity()).sum();
        BigDecimal totalAmount =
            skuRows.stream()
                .map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unitPrice = first.getUnitPrice();
        if (totalQty > 0 && (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0)) {
          unitPrice = totalAmount.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
        }

        String sourceOrders =
            skuRows.stream().map(OrderRow::getOrderNo).distinct().collect(Collectors.joining(","));

        OrderRow mergedRow =
            OrderRow.builder()
                .orderNo(mergedOrderNo + "-" + String.format("%03d", lineIndex++))
                .merchant(merchant)
                .productName(first.getProductName())
                .sku(first.getSku())
                .quantity(totalQty)
                .unitPrice(unitPrice)
                .amount(totalAmount)
                .receiver(first.getReceiver())
                .address(first.getAddress())
                .phone(first.getPhone())
                .sourceRowNum(first.getSourceRowNum())
                .build();
        mergedRow.setProductName(
            mergedRow.getProductName() + " [合单来源:" + sourceOrders + "]");
        merged.add(mergedRow);
      }
      merchantIndex++;
    }
    return merged;
  }

  public Map<String, List<OrderRow>> groupByMerchant(List<OrderRow> rows) {
    return rows.stream()
        .collect(Collectors.groupingBy(OrderRow::getMerchant, LinkedHashMap::new, Collectors.toList()));
  }

  private OrderRow copyRow(OrderRow source) {
    return OrderRow.builder()
        .orderNo(source.getOrderNo())
        .merchant(source.getMerchant())
        .productName(source.getProductName())
        .sku(source.getSku())
        .quantity(source.getQuantity())
        .unitPrice(source.getUnitPrice())
        .amount(source.getAmount())
        .receiver(source.getReceiver())
        .address(source.getAddress())
        .phone(source.getPhone())
        .shippingFee(source.getShippingFee())
        .remark(source.getRemark())
        .sourceRowNum(source.getSourceRowNum())
        .systemNo(source.getSystemNo())
        .build();
  }
}
