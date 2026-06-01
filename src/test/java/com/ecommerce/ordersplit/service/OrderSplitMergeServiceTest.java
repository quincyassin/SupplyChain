package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.model.OrderRow;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分单合单服务单元测试
 *
 * @author huangxinsong
 */
class OrderSplitMergeServiceTest {

  private OrderSplitMergeService service;

  @BeforeEach
  void setUp() {
    service = new OrderSplitMergeService();
  }

  @Test
  void groupByMerchant_shouldPreserveOriginalOrderNo() {
    List<OrderRow> input =
        Arrays.asList(
            buildRow("O1", "商家A", "商品1", "SKU1", 2),
            buildRow("O2", "商家B", "商品2", "SKU2", 1),
            buildRow("O3", "商家A", "商品3", "SKU3", 3));

    Map<String, List<OrderRow>> result = service.groupByMerchant(input);

    assertEquals(2, result.size());
    assertEquals(2, result.get("商家A").size());
    assertEquals(1, result.get("商家B").size());
    assertEquals("O1", result.get("商家A").get(0).getOrderNo());
    assertEquals("O3", result.get("商家A").get(1).getOrderNo());
    assertEquals("O2", result.get("商家B").get(0).getOrderNo());
  }

  private OrderRow buildRow(
      String orderNo, String merchant, String product, String sku, int quantity) {
    return OrderRow.builder()
        .orderNo(orderNo)
        .merchant(merchant)
        .productName(product)
        .sku(sku)
        .quantity(quantity)
        .unitPrice(new BigDecimal("50.00"))
        .amount(new BigDecimal("50.00").multiply(BigDecimal.valueOf(quantity)))
        .receiver("张三")
        .address("北京市")
        .phone("13800000000")
        .sourceRowNum(1)
        .build();
  }
}
