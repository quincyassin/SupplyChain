package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ImportedDateSummaryDto;
import com.ecommerce.ordersplit.dto.MerchantSplitGroupDto;
import com.ecommerce.ordersplit.dto.PlatformSummaryDto;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 导入订单查询测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ImportOrderQueryServiceTest {

  @Mock private ImportOrderRepository importOrderRepository;

  @Mock private ImportOrderPagedQueryService importOrderPagedQueryService;

  private ImportOrderQueryService queryService;

  @BeforeEach
  void setUp() {
    queryService =
        new ImportOrderQueryService(
            importOrderRepository, importOrderPagedQueryService, new DailyTableService());
  }

  @Test
  void listTodayOrders_shouldReturnPagedRowsAndSummaries() {
    ImportOrder taobao = buildOrder("V1StGXR8Z5jdHi6B", "淘宝", "商家A");
    ImportOrder pdd = buildOrder("AbCdEfGhIjKlMnOp", "拼多多", "商家A");

    when(importOrderPagedQueryService.findAllOrders(any()))
        .thenReturn(List.of(taobao, pdd));
    when(importOrderPagedQueryService.summarizeByMerchant(any()))
        .thenReturn(List.of(new MerchantSplitGroupDto("商家A", 2, 1, List.of())));
    when(importOrderPagedQueryService.summarizeByPlatform(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new PlatformSummaryDto("淘宝", 1, 0),
                new PlatformSummaryDto("拼多多", 1, 0)));

    SplitResultResponse response = queryService.listTodayOrders(10L);

    assertEquals(2, response.getTotalRows());
    assertEquals(2, response.getPlatformCount());
    assertEquals(1, response.getMerchantCount());
    assertEquals(1, response.getMerchantGroups().size());
    assertEquals("商家A", response.getMerchantGroups().get(0).getMerchant());
    assertEquals(2, response.getMerchantGroups().get(0).getRowCount());
    assertTrue(response.getMerchantGroups().get(0).getRows().isEmpty());
    assertEquals(2, response.getPageRows().size());
    assertEquals("淘宝", response.getPageRows().get(0).getPlatform());
    assertEquals("拼多多", response.getPageRows().get(1).getPlatform());
  }

  @Test
  void listTodayOrders_shouldUseUnknownPlatformWhenMissing() {
    ImportOrder order = buildOrder("V1StGXR8Z5jdHi6B", null, "商家A");

    when(importOrderPagedQueryService.findAllOrders(any()))
        .thenReturn(List.of(order));
    when(importOrderPagedQueryService.summarizeByMerchant(any()))
        .thenReturn(List.of(new MerchantSplitGroupDto("商家A", 1, 0, List.of())));
    when(importOrderPagedQueryService.summarizeByPlatform(any(), any(), any(), any()))
        .thenReturn(List.of(new PlatformSummaryDto(ImportOrderQueryService.UNKNOWN_PLATFORM, 1, 0)));

    SplitResultResponse response = queryService.listTodayOrders(null);

    assertEquals(
        ImportOrderQueryService.UNKNOWN_PLATFORM,
        response.getPageRows().get(0).getPlatform());
  }

  @Test
  void listRecentDateSummaries_shouldReturnFifteenDays() {
    when(importOrderRepository.countByIssueDateGreaterThanEqualAndIssueDateLessThan(any(), any()))
        .thenReturn(0L);

    List<ImportedDateSummaryDto> summaries = queryService.listRecentDateSummaries();

    assertEquals(ImportOrderQueryService.SIDEBAR_IMPORT_DAYS, summaries.size());
    assertEquals(true, summaries.get(0).isToday());
  }

  @Test
  void listOrdersByDateRange_shouldReturnRangeLabel() {
    ImportOrder dayOne = buildOrder("V1StGXR8Z5jdHi6B", "淘宝", "商家A");
    dayOne.setIssueDate(LocalDateTime.of(2026, 5, 27, 10, 0));
    ImportOrder dayTwo = buildOrder("AbCdEfGhIjKlMnOp", "拼多多", "商家B");
    dayTwo.setIssueDate(LocalDateTime.of(2026, 5, 28, 15, 0));

    when(importOrderPagedQueryService.findAllOrders(any()))
        .thenReturn(List.of(dayOne, dayTwo));
    when(importOrderPagedQueryService.summarizeByMerchant(any()))
        .thenReturn(
            List.of(
                new MerchantSplitGroupDto("商家A", 1, 0, List.of()),
                new MerchantSplitGroupDto("商家B", 1, 0, List.of())));
    when(importOrderPagedQueryService.summarizeByPlatform(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new PlatformSummaryDto("淘宝", 1, 0),
                new PlatformSummaryDto("拼多多", 1, 0)));

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate start = today.minusDays(1);
    SplitResultResponse response = queryService.listOrdersByDateRange(start, today, null);

    assertEquals(2, response.getTotalRows());
    assertEquals(2, response.getMerchantCount());
    assertEquals(start + " ~ " + today, response.getIssueDate());
  }

  @Test
  void listOrdersByDateRange_shouldPassKeywordToPagedQuery() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

    when(importOrderPagedQueryService.findAllOrders(any()))
        .thenReturn(List.of(buildOrder("V1StGXR8Z5jdHi6B", "淘宝", "商家A")));
    when(importOrderPagedQueryService.summarizeByMerchant(any()))
        .thenReturn(List.of(new MerchantSplitGroupDto("商家A", 1, 0, List.of())));
    when(importOrderPagedQueryService.summarizeByPlatform(any(), any(), any(), any()))
        .thenReturn(List.of(new PlatformSummaryDto("淘宝", 1, 0)));

    SplitResultResponse response =
        queryService.listOrdersByDateRange(today, today, null, "张三");

    assertEquals(1, response.getTotalRows());
  }

  @Test
  void listOrdersByDateRange_shouldRejectKeywordTooLong() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    String longKeyword = "a".repeat(65);
    org.junit.jupiter.api.Assertions.assertThrows(
        com.ecommerce.ordersplit.exception.BusinessException.class,
        () -> queryService.listOrdersByDateRange(today, today, null, longKeyword));
  }

  @Test
  void listOrdersByDateRange_shouldRejectInvertedRange() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate yesterday = today.minusDays(1);
    org.junit.jupiter.api.Assertions.assertThrows(
        com.ecommerce.ordersplit.exception.BusinessException.class,
        () -> queryService.listOrdersByDateRange(today, yesterday, null));
  }

  @Test
  void listOrdersByDateRange_shouldAcceptLongRange() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate start = today.minusDays(400);

    when(importOrderPagedQueryService.findAllOrders(any())).thenReturn(List.of());
    when(importOrderPagedQueryService.summarizeByMerchant(any())).thenReturn(List.of());
    when(importOrderPagedQueryService.summarizeByPlatform(any(), any(), any(), any()))
        .thenReturn(List.of());

    SplitResultResponse response = queryService.listOrdersByDateRange(start, today, null);

    assertEquals(start + " ~ " + today, response.getIssueDate());
  }

  @Test
  void listOrdersByDate_shouldAcceptHistoricalDate() {
    LocalDate oldDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(400);

    when(importOrderPagedQueryService.findAllOrders(any()))
        .thenReturn(List.of(buildOrder("V1StGXR8Z5jdHi6B", "淘宝", "商家A")));
    when(importOrderPagedQueryService.summarizeByMerchant(any()))
        .thenReturn(List.of(new MerchantSplitGroupDto("商家A", 1, 0, List.of())));
    when(importOrderPagedQueryService.summarizeByPlatform(any(), any(), any(), any()))
        .thenReturn(List.of(new PlatformSummaryDto("淘宝", 1, 0)));

    SplitResultResponse response = queryService.listOrdersByDate(oldDate, null);

    assertEquals(oldDate.toString(), response.getIssueDate());
  }

  @Test
  void listOrdersByDate_shouldRejectFutureDate() {
    LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
    org.junit.jupiter.api.Assertions.assertThrows(
        com.ecommerce.ordersplit.exception.BusinessException.class,
        () -> queryService.listOrdersByDate(tomorrow, null));
  }

  @Test
  void listTodayOrders_shouldExcludePendingSplitMerchantFromMerchantCount() {
    when(importOrderPagedQueryService.findAllOrders(any()))
        .thenReturn(List.of(buildOrder("V1StGXR8Z5jdHi6B", "淘宝", "商家A")));
    when(importOrderPagedQueryService.summarizeByMerchant(any()))
        .thenReturn(
            List.of(
                new MerchantSplitGroupDto(
                    MerchantConfigService.PENDING_SPLIT_MERCHANT, 3, 0, List.of()),
                new MerchantSplitGroupDto("商家A", 2, 1, List.of())));
    when(importOrderPagedQueryService.summarizeByPlatform(any(), any(), any(), any()))
        .thenReturn(List.of(new PlatformSummaryDto("淘宝", 5, 0)));

    SplitResultResponse response = queryService.listTodayOrders(10L);

    assertEquals(2, response.getMerchantGroups().size());
    assertEquals(1, response.getMerchantCount());
  }

  private ImportOrder buildOrder(String systemNo, String platform, String merchant) {
    ImportOrder order = new ImportOrder();
    order.setSystemNo(systemNo);
    order.setTaskId(10L);
    order.setPlatform(platform);
    order.setMerchant(merchant);
    order.setOrderNo("O-" + systemNo);
    order.setProductName("商品");
    order.setSpec("规格");
    order.setQuantity(1);
    order.setReceiver("张三");
    order.setAddress("地址");
    order.setPhone("13800000000");
    order.setShippingFee(new BigDecimal("10"));
    order.setReceiptStatus(ImportOrderReceiptStatus.PENDING);
    order.setIssueDate(LocalDateTime.of(2026, 5, 28, 12, 0));
    return order;
  }
}
