package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 售后订单导出服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class AfterSalesExportServiceTest {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock private ImportOrderPagedQueryService importOrderPagedQueryService;
    @Mock private ImportOrderQueryService importOrderQueryService;
    @Mock private ExcelWriterService excelWriterService;

    private AfterSalesExportService service;

    @BeforeEach
    void setUp() {
        service =
                new AfterSalesExportService(
                        importOrderPagedQueryService,
                        importOrderQueryService,
                        excelWriterService);
    }

    @Test
    void exportAfterSalesOrders_shouldSortPendingBeforeCompleted() throws IOException {
        LocalDate startDate = LocalDate.now(ZONE_SHANGHAI).minusDays(7);
        LocalDate endDate = LocalDate.now(ZONE_SHANGHAI);

        ImportOrder completedOrder = buildOrder("SYS-C", AfterSalesStatus.COMPLETED);
        ImportOrder pendingOrder = buildOrder("SYS-P", AfterSalesStatus.PENDING);

        when(importOrderQueryService.requireRecentDate(startDate)).thenReturn(startDate);
        when(importOrderQueryService.requireRecentDate(endDate)).thenReturn(endDate);
        when(importOrderQueryService.normalizeSearchKeyword("")).thenReturn("");
        when(importOrderPagedQueryService.findAllOrders(any())).thenReturn(
                List.of(completedOrder, pendingOrder));
        when(importOrderQueryService.toRowDto(completedOrder))
                .thenReturn(
                        DailyTableRowDto.builder()
                                .systemNo("SYS-C")
                                .afterSalesStatus("COMPLETED")
                                .afterSalesStatusLabel("售后完结")
                                .afterSalesAt("2026-05-28 10:00:00")
                                .build());
        when(importOrderQueryService.toRowDto(pendingOrder))
                .thenReturn(
                        DailyTableRowDto.builder()
                                .systemNo("SYS-P")
                                .afterSalesStatus("PENDING")
                                .afterSalesStatusLabel("需售后")
                                .afterSalesAt("2026-05-29 09:00:00")
                                .build());
        when(excelWriterService.writeAfterSalesTable(any())).thenReturn(new byte[] {1});

        service.exportAfterSalesOrders(startDate, endDate, "");

        ArgumentCaptor<List<DailyTableRowDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(excelWriterService).writeAfterSalesTable(captor.capture());
        List<DailyTableRowDto> exportedRows = captor.getValue();
        assertEquals(2, exportedRows.size());
        assertEquals("SYS-P", exportedRows.get(0).getSystemNo());
        assertEquals("SYS-C", exportedRows.get(1).getSystemNo());
    }

    @Test
    void exportAfterSalesOrders_shouldThrowWhenNoAfterSalesOrders() {
        LocalDate startDate = LocalDate.now(ZONE_SHANGHAI).minusDays(1);
        LocalDate endDate = LocalDate.now(ZONE_SHANGHAI);

        when(importOrderQueryService.requireRecentDate(startDate)).thenReturn(startDate);
        when(importOrderQueryService.requireRecentDate(endDate)).thenReturn(endDate);
        when(importOrderQueryService.normalizeSearchKeyword(null)).thenReturn("");
        when(importOrderPagedQueryService.findAllOrders(any())).thenReturn(List.of());

        assertThrows(
                BusinessException.class,
                () -> service.exportAfterSalesOrders(startDate, endDate, null));
    }

    @Test
    void afterSalesStatusSortOrder_shouldRankPendingFirst() {
        DailyTableRowDto pending =
                DailyTableRowDto.builder().afterSalesStatus("PENDING").build();
        DailyTableRowDto completed =
                DailyTableRowDto.builder().afterSalesStatus("COMPLETED").build();

        assertEquals(0, AfterSalesExportService.afterSalesStatusSortOrder(pending));
        assertEquals(1, AfterSalesExportService.afterSalesStatusSortOrder(completed));
    }

    private ImportOrder buildOrder(String systemNo, AfterSalesStatus status) {
        ImportOrder order = new ImportOrder();
        order.setSystemNo(systemNo);
        order.setAfterSales(true);
        order.setAfterSalesStatus(status);
        order.setIssueDate(LocalDateTime.now(ZONE_SHANGHAI));
        return order;
    }
}
