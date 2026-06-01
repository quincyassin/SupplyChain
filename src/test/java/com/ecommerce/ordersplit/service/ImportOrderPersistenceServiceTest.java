package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.AssignMerchantPersistenceResult;
import com.ecommerce.ordersplit.dto.UpdateImportedOrderFieldsRequest;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.model.OrderRow;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.ecommerce.ordersplit.service.ProductPriceService.ImportPriceLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentMatchers;

/**
 * 导入订单持久化测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ImportOrderPersistenceServiceTest {

  private static final String SYSTEM_NO_1 = "V1StGXR8Z5jdHi6B";
  private static final String SYSTEM_NO_2 = "AbCdEfGhIjKlMnOp";

  @Mock private ImportOrderRepository importOrderRepository;

  @Mock private MerchantConfigService merchantConfigService;

  @Mock private OrderSplitMergeService orderSplitMergeService;

  @Mock private ProductPriceService productPriceService;

  private ImportOrderPersistenceService persistenceService;

  @BeforeEach
  void setUp() {
    persistenceService =
        new ImportOrderPersistenceService(
            importOrderRepository,
            new DailyTableService(),
            merchantConfigService,
            orderSplitMergeService,
            productPriceService);
  }

  @Test
  void saveSplitOrders_shouldPersistRows() {
    Map<String, List<OrderRow>> split = new LinkedHashMap<>();
    split.put(
        "商家A",
        List.of(
            OrderRow.builder()
                .orderNo("O001")
                .merchant("商家A")
                .productName("商品")
                .sku("规格")
                .quantity(1)
                .receiver("张三")
                .address("地址1")
                .phone("13800000000")
                .shippingFee(new BigDecimal("10"))
                .sourceRowNum(2)
                .build()));

    when(productPriceService.buildLookupForImport(anyList()))
        .thenReturn(new ImportPriceLookup(Map.of(), Map.of()));

    int saved =
        persistenceService.saveSplitOrders(
            100L, "淘宝", split, new DailyTableService().currentIssueDateTime());

    assertEquals(1, saved);
    ArgumentCaptor<List<ImportOrder>> captor = ArgumentCaptor.forClass(List.class);
    verify(importOrderRepository).saveAll(captor.capture());
    ImportOrder row = captor.getValue().get(0);
    assertEquals(100L, row.getTaskId());
    assertEquals("淘宝", row.getPlatform());
    assertEquals("商家A", row.getMerchant());
    assertEquals(true, row.getMerchantSplit());
    assertEquals("O001", row.getOrderNo());
    assertEquals(ImportOrderReceiptStatus.PENDING, row.getReceiptStatus());
    assertEquals(10, row.getSystemNo().length());
    assertTrue(row.getSystemNo().matches("\\d{10}"));
    verify(productPriceService).buildLookupForImport(captor.getValue());
    verify(productPriceService).applyConfiguredPrices(eq(row), any(ImportPriceLookup.class));
  }

  @Test
  void deleteTodayOrder_shouldRemoveEntity() {
    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    when(importOrderRepository.findBySystemNoInOrderByMerchantAscSystemNoAsc(
            List.of(SYSTEM_NO_1)))
        .thenReturn(List.of(entity));

    persistenceService.deleteOrderForDate(
        SYSTEM_NO_1, LocalDate.now(ZoneId.of("Asia/Shanghai")));

    verify(importOrderRepository).deleteAll(List.of(entity));
  }

  @Test
  void deleteTodayOrders_shouldRemoveMultiple() {
    ImportOrder first = new ImportOrder();
    first.setSystemNo(SYSTEM_NO_1);
    first.setIssueDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    ImportOrder second = new ImportOrder();
    second.setSystemNo(SYSTEM_NO_2);
    second.setIssueDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    when(importOrderRepository.findBySystemNoInOrderByMerchantAscSystemNoAsc(
            List.of(SYSTEM_NO_1, SYSTEM_NO_2)))
        .thenReturn(List.of(first, second));

    int deleted =
        persistenceService.deleteOrdersForDate(
            List.of(SYSTEM_NO_1, SYSTEM_NO_2),
            LocalDate.now(ZoneId.of("Asia/Shanghai")));

    assertEquals(2, deleted);
    verify(importOrderRepository).deleteAll(ArgumentMatchers.eq(List.of(first, second)));
  }

  @Test
  void updateOrderMerchant_shouldUpdateOrderOnly() {
    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
    entity.setIssueDate(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    persistenceService.updateOrderMerchant(
        SYSTEM_NO_1, LocalDate.now(ZoneId.of("Asia/Shanghai")), "手工商家");

    assertEquals("手工商家", entity.getMerchant());
    assertEquals(true, entity.getMerchantSplit());
    verify(importOrderRepository).save(entity);
    verify(merchantConfigService).ensureManualMerchant("手工商家");
  }

  @Test
  void updateOrderMerchant_shouldRejectPendingName() {
    assertThrows(
        com.ecommerce.ordersplit.exception.BusinessException.class,
        () ->
            persistenceService.updateOrderMerchant(
                SYSTEM_NO_1,
                LocalDate.now(ZoneId.of("Asia/Shanghai")),
                MerchantConfigService.PENDING_SPLIT_MERCHANT));
  }

  @Test
  void assignAllPendingMerchants_shouldSaveMatchedAndKeepUnmatchedAsPending() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(10);

    ImportOrder matched = new ImportOrder();
    matched.setSystemNo(SYSTEM_NO_1);
    matched.setMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
    matched.setProductName("匹配商品");
    matched.setSourceRowNum(2);
    matched.setIssueDate(issueDate);

    ImportOrder unmatched = new ImportOrder();
    unmatched.setSystemNo(SYSTEM_NO_2);
    unmatched.setMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
    unmatched.setProductName("未知商品");
    unmatched.setSourceRowNum(3);
    unmatched.setIssueDate(issueDate);

    when(importOrderRepository
            .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(List.of(matched, unmatched));
    when(merchantConfigService.resolveByProductName("匹配商品")).thenReturn("商家A");
    when(merchantConfigService.resolveByProductName("未知商品"))
        .thenReturn(MerchantConfigService.UNMATCHED_MERCHANT_NAME);

    OrderRow assignedRow =
        OrderRow.builder()
            .merchant("商家A")
            .orderNo("O-001")
            .productName("匹配商品")
            .sourceRowNum(2)
            .systemNo(SYSTEM_NO_1)
            .build();
    OrderRow pendingRow =
        OrderRow.builder()
            .merchant(MerchantConfigService.PENDING_SPLIT_MERCHANT)
            .orderNo("O-002")
            .productName("未知商品")
            .sourceRowNum(3)
            .systemNo(SYSTEM_NO_2)
            .build();
    when(orderSplitMergeService.groupByMerchant(ArgumentMatchers.anyList()))
        .thenReturn(
            Map.of(
                "商家A", List.of(assignedRow),
                MerchantConfigService.PENDING_SPLIT_MERCHANT, List.of(pendingRow)));

    AssignMerchantPersistenceResult result =
        persistenceService.assignPendingMerchantsInRange(today, today);

    assertEquals(2, result.processedCount());
    assertEquals(1, result.unmatchedPendingCount());
    assertEquals(2, result.processedOrders().size());
    assertEquals(SYSTEM_NO_1, result.processedOrders().get(0).getSystemNo());
    assertEquals(SYSTEM_NO_2, result.processedOrders().get(1).getSystemNo());
    assertEquals("商家A", matched.getMerchant());
    assertNull(matched.getOrderNo());
    assertEquals(MerchantConfigService.PENDING_SPLIT_MERCHANT, unmatched.getMerchant());
    assertEquals(true, matched.getMerchantSplit());
    assertEquals(true, unmatched.getMerchantSplit());
    verify(importOrderRepository).saveAll(List.of(matched, unmatched));
  }

  @Test
  void assignPendingMerchantsInRange_shouldKeepPreviouslyAssignedMerchant() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(10);

    ImportOrder assigned = new ImportOrder();
    assigned.setSystemNo(SYSTEM_NO_1);
    assigned.setMerchant("旧商家");
    assigned.setMerchantSplit(true);
    assigned.setProductName("匹配商品");
    assigned.setOrderNo("OLD-001");
    assigned.setSourceRowNum(2);
    assigned.setIssueDate(issueDate);

    ImportOrder pending = new ImportOrder();
    pending.setSystemNo(SYSTEM_NO_2);
    pending.setMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
    pending.setProductName("匹配商品");
    pending.setSourceRowNum(3);
    pending.setIssueDate(issueDate);

    when(importOrderRepository
            .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(List.of(assigned, pending));
    when(merchantConfigService.resolveByProductName("匹配商品")).thenReturn("新商家");
    when(orderSplitMergeService.groupByMerchant(ArgumentMatchers.anyList()))
        .thenReturn(
            Map.of(
                "新商家",
                List.of(
                    OrderRow.builder()
                        .merchant("新商家")
                        .orderNo("O-002")
                        .productName("匹配商品")
                        .sourceRowNum(3)
                        .systemNo(SYSTEM_NO_2)
                        .build())));

    AssignMerchantPersistenceResult result =
        persistenceService.assignPendingMerchantsInRange(today, today);

    assertEquals(2, result.processedCount());
    assertEquals("旧商家", assigned.getMerchant());
    assertEquals("OLD-001", assigned.getOrderNo());
    assertEquals("新商家", pending.getMerchant());
    assertNull(pending.getOrderNo());
    verify(importOrderRepository).saveAll(List.of(pending));
    verify(importOrderRepository, never()).saveAll(List.of(assigned, pending));
  }

  @Test
  void markOrderAfterSales_shouldPersistRemarkAndTimestamp() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setAfterSales(false);

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    persistenceService.markOrderAfterSales(SYSTEM_NO_1, today, "  商品破损  ");

    assertEquals(true, entity.getAfterSales());
    assertEquals(AfterSalesStatus.PENDING, entity.getAfterSalesStatus());
    assertEquals("商品破损", entity.getAfterSalesRemark());
    assertEquals(issueDate, entity.getIssueDate());
    verify(importOrderRepository).save(entity);
  }

  @Test
  void markOrderAfterSales_shouldRejectBlankRemark() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    assertThrows(
        com.ecommerce.ordersplit.exception.BusinessException.class,
        () -> persistenceService.markOrderAfterSales(SYSTEM_NO_1, today, "   "));
    verifyNoInteractions(importOrderRepository);
  }

  @Test
  void cancelOrderAfterSales_shouldClearFlagAndRemark() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setAfterSales(true);
    entity.setAfterSalesStatus(AfterSalesStatus.PENDING);
    entity.setAfterSalesRemark("商品破损");
    entity.setAfterSalesAt(issueDate.plusHours(1));

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    persistenceService.cancelOrderAfterSales(SYSTEM_NO_1, today);

    assertEquals(false, entity.getAfterSales());
    assertEquals(AfterSalesStatus.NONE, entity.getAfterSalesStatus());
    assertEquals(null, entity.getAfterSalesRemark());
    assertEquals(null, entity.getAfterSalesAt());
    verify(importOrderRepository).save(entity);
  }

  @Test
  void completeOrderAfterSales_shouldMarkCompleted() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setAfterSales(true);
    entity.setAfterSalesStatus(AfterSalesStatus.PENDING);
    entity.setAfterSalesRemark("商品破损");
    entity.setAfterSalesAt(issueDate.plusHours(1));

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    persistenceService.completeOrderAfterSales(SYSTEM_NO_1, today);

    assertEquals(true, entity.getAfterSales());
    assertEquals(AfterSalesStatus.COMPLETED, entity.getAfterSalesStatus());
    assertEquals("商品破损", entity.getAfterSalesRemark());
    assertEquals(issueDate.plusHours(1), entity.getAfterSalesAt());
    verify(importOrderRepository).save(entity);
  }

  @Test
  void cancelOrderAfterSales_shouldRejectWhenNotPending() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setAfterSales(true);
    entity.setAfterSalesStatus(AfterSalesStatus.COMPLETED);

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    assertThrows(
        com.ecommerce.ordersplit.exception.BusinessException.class,
        () -> persistenceService.cancelOrderAfterSales(SYSTEM_NO_1, today));
    verify(importOrderRepository, never()).save(entity);
  }

  @Test
  void updateOrderFields_shouldPersistEditableValues() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setOrderNo("OLD-001");
    entity.setReceiver("张三");

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    UpdateImportedOrderFieldsRequest request = new UpdateImportedOrderFieldsRequest();
    request.setOrderNo("NEW-001");
    request.setPhone("13900000000");
    request.setRemark("  加急发货  ");

    persistenceService.updateOrderFields(SYSTEM_NO_1, today, request);

    assertEquals("NEW-001", entity.getOrderNo());
    assertEquals("13900000000", entity.getPhone());
    assertEquals("加急发货", entity.getRemark());
    assertEquals("张三", entity.getReceiver());
    verify(importOrderRepository).save(entity);
  }

  @Test
  void updateOrderFields_shouldPersistShippingFee() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setShippingFee(BigDecimal.ZERO);

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    UpdateImportedOrderFieldsRequest request = new UpdateImportedOrderFieldsRequest();
    request.setShippingFee(new BigDecimal("12.567"));

    persistenceService.updateOrderFields(SYSTEM_NO_1, today, request);

    assertEquals(new BigDecimal("12.57"), entity.getShippingFee());
    verify(importOrderRepository).save(entity);
  }

  @Test
  void updateOrderFields_shouldMarkReceiptedWhenBothLogisticsFieldsFilled() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setReceiptStatus(ImportOrderReceiptStatus.PENDING);
    entity.setLogisticsCompany("顺丰");

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    UpdateImportedOrderFieldsRequest request = new UpdateImportedOrderFieldsRequest();
    request.setLogisticsNo("SF1234567890");

    persistenceService.updateOrderFields(SYSTEM_NO_1, today, request);

    assertEquals("SF1234567890", entity.getLogisticsNo());
    assertEquals(ImportOrderReceiptStatus.RECEIPTED, entity.getReceiptStatus());
    verify(importOrderRepository).save(entity);
  }

  @Test
  void updateOrderFields_shouldKeepPendingWhenLogisticsIncomplete() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime issueDate = today.atStartOfDay().plusHours(9);

    ImportOrder entity = new ImportOrder();
    entity.setSystemNo(SYSTEM_NO_1);
    entity.setIssueDate(issueDate);
    entity.setReceiptStatus(ImportOrderReceiptStatus.PENDING);

    when(importOrderRepository.findById(SYSTEM_NO_1)).thenReturn(Optional.of(entity));

    UpdateImportedOrderFieldsRequest request = new UpdateImportedOrderFieldsRequest();
    request.setLogisticsNo("SF1234567890");

    persistenceService.updateOrderFields(SYSTEM_NO_1, today, request);

    assertEquals(ImportOrderReceiptStatus.PENDING, entity.getReceiptStatus());
    verify(importOrderRepository).save(entity);
  }
}
