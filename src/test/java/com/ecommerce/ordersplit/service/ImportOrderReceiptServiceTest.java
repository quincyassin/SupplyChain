package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 导入订单回单维护测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ImportOrderReceiptServiceTest {

  @Mock private ImportOrderRepository importOrderRepository;

  @Mock private ImportOrderQueryService importOrderQueryService;

  private ImportOrderReceiptService receiptService;

  @BeforeEach
  void setUp() {
    receiptService = new ImportOrderReceiptService(importOrderRepository, importOrderQueryService);
  }

  @Test
  void batchUpdateReceipt_shouldUpdateMatchedOrdersBySystemNo() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(10);
    ImportOrder order = new ImportOrder();
    order.setSystemNo("0123456789");
    order.setOrderNo("O001");
    order.setIssueDate(issueDate);
    order.setReceiptStatus(ImportOrderReceiptStatus.PENDING);

    when(importOrderQueryService.requireRecentDate(today)).thenReturn(today);
    when(importOrderRepository
            .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                any(), any()))
        .thenReturn(List.of(order));
    when(importOrderQueryService.listOrdersByDate(today, null))
        .thenReturn(
            new SplitResultResponse(
                null, today.toString(), 1, 1, 1, List.of(), List.of(), List.of(), 0));

    String systemNo = order.getSystemNo();
    var response =
        receiptService.batchUpdateReceipt(today, systemNo + "\tSF1234567890\t顺丰");

    assertEquals(1, response.getUpdatedCount());
    assertEquals(ImportOrderReceiptStatus.RECEIPTED, order.getReceiptStatus());
    assertEquals("SF1234567890", order.getLogisticsNo());
    assertEquals("顺丰", order.getLogisticsCompany());
  }
}
