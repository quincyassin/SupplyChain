package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    when(importOrderQueryService.listOrdersByDateRange(eq(today), eq(today), eq(null)))
        .thenReturn(
            new SplitResultResponse(
                null, today.toString(), 1, 1, 1, List.of(), List.of(), List.of(), 0));

    String systemNo = order.getSystemNo();
    var response =
        receiptService.batchUpdateReceipt(today, today, systemNo + "\tSF1234567890\t顺丰");

    assertEquals(1, response.getUpdatedCount());
    assertEquals(ImportOrderReceiptStatus.RECEIPTED, order.getReceiptStatus());
    assertEquals("SF1234567890", order.getLogisticsNo());
    assertEquals("顺丰", order.getLogisticsCompany());
  }

  @Test
  void batchUpdateReceipt_shouldMatchOrdersWithinSelectedDateRange() {
    LocalDate start = LocalDate.of(2026, 5, 30);
    LocalDate end = LocalDate.of(2026, 6, 1);
    ImportOrder orderOnStart = buildPendingOrder("0111111111", start.atStartOfDay().plusHours(9));
    ImportOrder orderOnEnd = buildPendingOrder("0222222222", end.atStartOfDay().plusHours(9));

    when(importOrderQueryService.requireRecentDate(start)).thenReturn(start);
    when(importOrderQueryService.requireRecentDate(end)).thenReturn(end);
    when(importOrderRepository
            .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                any(), any()))
        .thenReturn(List.of(orderOnStart, orderOnEnd));
    when(importOrderQueryService.listOrdersByDateRange(eq(start), eq(end), eq(null)))
        .thenReturn(
            new SplitResultResponse(
                null, start + " ~ " + end, 2, 2, 2, List.of(), List.of(), List.of(), 0));

    var response =
        receiptService.batchUpdateReceipt(
            start,
            end,
            "0111111111\tSF1001\t顺丰\n0222222222\tSF2002\t圆通");

    assertEquals(2, response.getUpdatedCount());
    assertEquals(ImportOrderReceiptStatus.RECEIPTED, orderOnStart.getReceiptStatus());
    assertEquals(ImportOrderReceiptStatus.RECEIPTED, orderOnEnd.getReceiptStatus());
  }

  @Test
  void batchUpdateReceipt_shouldRejectWhenSystemNoNotInSelectedRange() {
    LocalDate start = LocalDate.of(2026, 6, 1);
    LocalDate end = LocalDate.of(2026, 6, 3);

    when(importOrderQueryService.requireRecentDate(start)).thenReturn(start);
    when(importOrderQueryService.requireRecentDate(end)).thenReturn(end);
    when(importOrderRepository
            .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                any(), any()))
        .thenReturn(List.of());

    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> receiptService.batchUpdateReceipt(start, end, "0999999999\tSF1234567890\t顺丰"));

    assertEquals("未匹配到任何订单，请确认系统单号在所选分单日期区间内", ex.getMessage());
  }

  private ImportOrder buildPendingOrder(String systemNo, LocalDateTime issueDate) {
    ImportOrder order = new ImportOrder();
    order.setSystemNo(systemNo);
    order.setOrderNo("O-" + systemNo);
    order.setIssueDate(issueDate);
    order.setReceiptStatus(ImportOrderReceiptStatus.PENDING);
    return order;
  }
}
