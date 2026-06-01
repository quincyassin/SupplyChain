package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ImportOrderArchivePreviewDto;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.repository.ImportOrderArchiveRepository;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单物理归档测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ImportOrderArchiveServiceTest {

    @Mock private ImportOrderRepository importOrderRepository;

    @Mock private ImportOrderArchiveRepository importOrderArchiveRepository;

    @Mock private ImportOrderArchivePagedQueryService importOrderArchivePagedQueryService;

    @Mock private ImportOrderQueryService importOrderQueryService;

    @Mock private EntityManager entityManager;

    @Mock private Query nativeQuery;

    private ImportOrderArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService =
                new ImportOrderArchiveService(
                        importOrderRepository,
                        importOrderArchiveRepository,
                        importOrderArchivePagedQueryService,
                        importOrderQueryService);
        org.springframework.test.util.ReflectionTestUtils.setField(
                archiveService, "entityManager", entityManager);
    }

    @Test
    void previewArchive_shouldReturnCounts() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 6, 30);
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();
        when(importOrderRepository.countByIssueDateGreaterThanEqualAndIssueDateLessThan(
                        rangeStart, rangeEnd))
                .thenReturn(100L);
        when(importOrderRepository.countInIssueDateRangeAndAfterSalesStatus(
                        rangeStart, rangeEnd, AfterSalesStatus.PENDING))
                .thenReturn(3L);
        when(importOrderRepository.countInIssueDateRangeAndAfterSalesStatus(
                        rangeStart, rangeEnd, AfterSalesStatus.COMPLETED))
                .thenReturn(7L);

        ImportOrderArchivePreviewDto preview = archiveService.previewArchive(start, end);

        assertEquals("2024-01-01 ~ 2024-06-30", preview.getBeforeDate());
        assertEquals(100L, preview.getOrderCount());
        assertEquals(3L, preview.getPendingAfterSalesCount());
        assertEquals(7L, preview.getCompletedAfterSalesCount());
    }

    @Test
    void archiveDateRange_shouldRejectWhenEmpty() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();
        when(importOrderRepository.countByIssueDateGreaterThanEqualAndIssueDateLessThan(
                        rangeStart, rangeEnd))
                .thenReturn(0L);

        assertThrows(BusinessException.class, () -> archiveService.archiveDateRange(start, end));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void restoreDateRange_shouldRejectWhenConflictExists() {
        LocalDate start = LocalDate.of(2024, 6, 1);
        LocalDate end = LocalDate.of(2024, 6, 30);
        when(importOrderArchiveRepository.countInIssueDateRange(
                        start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(10L);
        when(importOrderArchiveRepository.findConflictSystemNosInRange(
                        start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(java.util.List.of("V1StGXR8Z5"));

        assertThrows(
                BusinessException.class, () -> archiveService.restoreDateRange(start, end));
        verify(entityManager, never()).createNativeQuery(contains("INSERT INTO import_order"));
    }
}
