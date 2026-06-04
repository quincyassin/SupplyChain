package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.model.OrderRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 当日表格服务单元测试
 *
 * @author huangxinsong
 */
class DailyTableServiceTest {

  private final DailyTableService service = new DailyTableService();

  @Test
  void buildDailyTable_shouldFillIssueDateAndColumns() {
    OrderRow row =
        OrderRow.builder()
            .orderNo("O001")
            .productName("蓝牙耳机")
            .sku("黑色")
            .quantity(2)
            .receiver("张三")
            .address("北京市朝阳区")
            .phone("13800000001")
            .shippingFee(new BigDecimal("12.5"))
            .logisticsNo("SF1234567890")
            .logisticsCompany("顺丰")
            .build();

    List<DailyTableRowDto> result = service.buildDailyTable(List.of(row));

    assertEquals(1, result.size());
    DailyTableRowDto dto = result.get(0);
    assertEquals("O001", dto.getOrderNo());
    assertEquals("蓝牙耳机", dto.getProductName());
    assertEquals("黑色", dto.getSpec());
    assertEquals(2, dto.getQuantity());
    assertEquals("张三", dto.getReceiver());
    assertEquals("北京市朝阳区", dto.getAddress());
    assertEquals("13800000001", dto.getPhone());
    assertEquals(new BigDecimal("12.5"), dto.getShippingFee());
    assertEquals("SF1234567890", dto.getLogisticsNo());
    assertEquals("顺丰", dto.getLogisticsCompany());
    assertNotNull(dto.getIssueDate());
  }
}
