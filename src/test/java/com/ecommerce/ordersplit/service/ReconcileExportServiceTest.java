package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商家/平台对账导出服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ReconcileExportServiceTest {

    @Mock private ImportOrderRepository importOrderRepository;
    @Mock private ImportOrderQueryService importOrderQueryService;
    @Mock private ExcelWriterService excelWriterService;

    private ReconcileExportService service;

    @BeforeEach
    void setUp() {
        service =
                new ReconcileExportService(
                        importOrderRepository, importOrderQueryService, excelWriterService);
    }

    @Test
    void exportMerchantReconcile_shouldExportRowsInIssueDateAscendingOrder() throws IOException {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 3);
        ImportOrder orderDay1 = buildOrder("1111111111", "商家A", "平台B", startDate);
        ImportOrder orderDay2 = buildOrder("2222222222", "商家A", "平台A", startDate.plusDays(1));
        ImportOrder orderDay3 = buildOrder("3333333333", "商家A", "平台A", startDate.plusDays(2));
        ImportOrder otherMerchant = buildOrder("4444444444", "商家B", "平台A", startDate);

        when(importOrderQueryService.requireRecentDate(startDate)).thenReturn(startDate);
        when(importOrderQueryService.requireRecentDate(endDate)).thenReturn(endDate);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByIssueDateAsc(
                                any(), any()))
                .thenReturn(List.of(orderDay1, orderDay2, orderDay3, otherMerchant));
        when(importOrderQueryService.toRowDto(orderDay1))
                .thenReturn(rowDto("1111111111", "2026-06-01"));
        when(importOrderQueryService.toRowDto(orderDay2))
                .thenReturn(rowDto("2222222222", "2026-06-02"));
        when(importOrderQueryService.toRowDto(orderDay3))
                .thenReturn(rowDto("3333333333", "2026-06-03"));
        when(excelWriterService.writeMerchantReconcileTable(any(), any()))
                .thenReturn(new byte[] {1});

        service.exportMerchantReconcile(startDate, endDate, "商家A");

        verify(importOrderRepository)
                .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByIssueDateAsc(
                        eq(startDate.atStartOfDay()), eq(endDate.plusDays(1).atStartOfDay()));

        ArgumentCaptor<List<DailyTableRowDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(excelWriterService).writeMerchantReconcileTable(eq("商家A对账"), captor.capture());
        List<DailyTableRowDto> exportedRows = captor.getValue();
        assertEquals(3, exportedRows.size());
        assertEquals("1111111111", exportedRows.get(0).getSystemNo());
        assertEquals("2222222222", exportedRows.get(1).getSystemNo());
        assertEquals("3333333333", exportedRows.get(2).getSystemNo());
    }

    @Test
    void exportPlatformReconcile_shouldExportRowsInIssueDateAscendingOrder() throws IOException {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 3);
        ImportOrder orderDay1 = buildOrder("1111111111", "商家B", "平台A", startDate);
        ImportOrder orderDay2 = buildOrder("2222222222", "商家A", "平台A", startDate.plusDays(1));
        ImportOrder orderDay3 = buildOrder("3333333333", "商家C", "平台A", startDate.plusDays(2));
        ImportOrder otherPlatform = buildOrder("4444444444", "商家A", "平台B", startDate);

        when(importOrderQueryService.requireRecentDate(startDate)).thenReturn(startDate);
        when(importOrderQueryService.requireRecentDate(endDate)).thenReturn(endDate);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByIssueDateAsc(
                                any(), any()))
                .thenReturn(List.of(orderDay1, orderDay2, orderDay3, otherPlatform));
        when(importOrderQueryService.toRowDto(orderDay1))
                .thenReturn(rowDto("1111111111", "2026-06-01"));
        when(importOrderQueryService.toRowDto(orderDay2))
                .thenReturn(rowDto("2222222222", "2026-06-02"));
        when(importOrderQueryService.toRowDto(orderDay3))
                .thenReturn(rowDto("3333333333", "2026-06-03"));
        when(excelWriterService.writePlatformReconcileTable(any(), any()))
                .thenReturn(new byte[] {1});

        service.exportPlatformReconcile(startDate, endDate, "平台A");

        ArgumentCaptor<List<DailyTableRowDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(excelWriterService).writePlatformReconcileTable(eq("平台A对账"), captor.capture());
        List<DailyTableRowDto> exportedRows = captor.getValue();
        assertEquals(3, exportedRows.size());
        assertEquals("2026-06-01", exportedRows.get(0).getIssueDate());
        assertEquals("2026-06-02", exportedRows.get(1).getIssueDate());
        assertEquals("2026-06-03", exportedRows.get(2).getIssueDate());
    }

    @Test
    void exportMerchantReconcile_shouldThrowWhenNoOrdersForMerchant() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 3);

        when(importOrderQueryService.requireRecentDate(startDate)).thenReturn(startDate);
        when(importOrderQueryService.requireRecentDate(endDate)).thenReturn(endDate);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByIssueDateAsc(
                                any(), any()))
                .thenReturn(List.of());

        assertThrows(
                BusinessException.class,
                () -> service.exportMerchantReconcile(startDate, endDate, "商家A"));
    }

    private ImportOrder buildOrder(
            String systemNo, String merchant, String platform, LocalDate issueDate) {
        ImportOrder order = new ImportOrder();
        order.setSystemNo(systemNo);
        order.setMerchant(merchant);
        order.setPlatform(platform);
        order.setIssueDate(issueDate.atStartOfDay().plusHours(10));
        return order;
    }

    private DailyTableRowDto rowDto(String systemNo, String issueDate) {
        return DailyTableRowDto.builder().systemNo(systemNo).issueDate(issueDate).build();
    }
}
