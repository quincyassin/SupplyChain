package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ImportOrderRecycleBin;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.repository.ImportOrderRecycleBinRepository;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单回收站测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ImportOrderRecycleBinServiceTest {

    private static final String SYSTEM_NO_1 = "V1StGXR8Z5jdHi6B";

    @Mock private ImportOrderRepository importOrderRepository;

    @Mock private ImportOrderRecycleBinRepository importOrderRecycleBinRepository;

    @Mock private ImportOrderRecycleBinPagedQueryService importOrderRecycleBinPagedQueryService;

    @Mock private ImportOrderQueryService importOrderQueryService;

    @Mock private DailyTableService dailyTableService;

    @Mock private EntityManager entityManager;

    @Mock private Query nativeQuery;

    private ImportOrderRecycleBinService recycleBinService;

    @BeforeEach
    void setUp() {
        recycleBinService =
                new ImportOrderRecycleBinService(
                        importOrderRepository,
                        importOrderRecycleBinRepository,
                        importOrderRecycleBinPagedQueryService,
                        importOrderQueryService,
                        dailyTableService);
        org.springframework.test.util.ReflectionTestUtils.setField(
                recycleBinService, "entityManager", entityManager);
    }

    @Test
    void restoreSelected_shouldRejectWhenMainTableHasConflict() {
        ImportOrderRecycleBin recycleBin = new ImportOrderRecycleBin();
        recycleBin.setSystemNo(SYSTEM_NO_1);
        when(importOrderRecycleBinRepository.findBySystemNoInOrderByDeletedAtDescSystemNoDesc(
                        List.of(SYSTEM_NO_1)))
                .thenReturn(List.of(recycleBin));
        ImportOrder conflict = new ImportOrder();
        conflict.setSystemNo(SYSTEM_NO_1);
        when(importOrderRepository.findBySystemNoInOrderByMerchantAscSystemNoAsc(
                        List.of(SYSTEM_NO_1)))
                .thenReturn(List.of(conflict));

        assertThrows(
                BusinessException.class,
                () -> recycleBinService.restoreSelected(List.of(SYSTEM_NO_1)));

        verify(entityManager, never()).createNativeQuery(contains("INSERT INTO import_order"));
    }

    @Test
    void purgeSelected_shouldDeleteRecycleBinRows() {
        ImportOrderRecycleBin recycleBin = new ImportOrderRecycleBin();
        recycleBin.setSystemNo(SYSTEM_NO_1);
        recycleBin.setDeletedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        when(importOrderRecycleBinRepository.findBySystemNoInOrderByDeletedAtDescSystemNoDesc(
                        List.of(SYSTEM_NO_1)))
                .thenReturn(List.of(recycleBin));

        var result = recycleBinService.purgeSelected(List.of(SYSTEM_NO_1));

        assertEquals(1, result.affectedCount());
        verify(importOrderRecycleBinRepository).deleteAll(List.of(recycleBin));
    }

    @Test
    void restoreSelected_shouldRejectEmptySelection() {
        assertThrows(BusinessException.class, () -> recycleBinService.restoreSelected(List.of()));
        verify(importOrderRecycleBinRepository, never()).deleteAll(anyList());
    }
}
