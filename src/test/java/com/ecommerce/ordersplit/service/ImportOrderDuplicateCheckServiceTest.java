package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ImportDuplicatePreviewDto;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.ImportDuplicateReason;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.model.OrderRow;
import com.ecommerce.ordersplit.repository.ImportOrderArchiveRepository;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入订单编号重复检测测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ImportOrderDuplicateCheckServiceTest {

    @Mock
    private ImportOrderRepository importOrderRepository;

    @Mock
    private ImportOrderArchiveRepository importOrderArchiveRepository;

    private ImportOrderDuplicateCheckService duplicateCheckService;

    @BeforeEach
    void setUp() {
        duplicateCheckService =
                new ImportOrderDuplicateCheckService(importOrderRepository, importOrderArchiveRepository);
    }

    @Test
    void buildPreview_shouldSkipWhenOrderNoNotMapped() {
        ImportDuplicatePreviewDto preview =
                duplicateCheckService.buildPreview(sampleRows(), mappingWithoutOrderNo());

        assertFalse(preview.isOrderNoMapped());
        assertEquals(0, preview.getDuplicateRowCount());
        verify(importOrderRepository, never()).findExistingOrderNos(anyCollection());
    }

    @Test
    void buildPreview_shouldDetectFileDuplicate() {
        when(importOrderRepository.findExistingOrderNos(anyCollection())).thenReturn(List.of());
        when(importOrderArchiveRepository.findExistingOrderNos(anyCollection())).thenReturn(List.of());

        ImportDuplicatePreviewDto preview =
                duplicateCheckService.buildPreview(
                        List.of(
                                row(2, "A001", "商品1"),
                                row(3, "A001", "商品2"),
                                row(4, "A002", "商品3")),
                        mappingWithOrderNo());

        assertTrue(preview.isOrderNoMapped());
        assertEquals(1, preview.getDuplicateRowCount());
        assertEquals(List.of("A001"), preview.getDuplicateOrderNos());
        assertEquals(3, preview.getDuplicateRows().get(0).getSourceRowNum());
        assertEquals(ImportDuplicateReason.FILE, preview.getDuplicateRows().get(0).getDuplicateReason());
    }

    @Test
    void buildPreview_shouldDetectDatabaseDuplicate() {
        when(importOrderRepository.findExistingOrderNos(anyCollection())).thenReturn(List.of("B001"));
        when(importOrderArchiveRepository.findExistingOrderNos(anyCollection())).thenReturn(List.of());

        ImportDuplicatePreviewDto preview =
                duplicateCheckService.buildPreview(
                        List.of(row(2, "B001", "商品1"), row(3, "B002", "商品2")),
                        mappingWithOrderNo());

        assertEquals(1, preview.getDuplicateRowCount());
        assertEquals(List.of("B001"), preview.getDuplicateOrderNos());
        assertEquals(ImportDuplicateReason.DATABASE, preview.getDuplicateRows().get(0).getDuplicateReason());
    }

    @Test
    void filterForImport_shouldKeepFirstOccurrenceAndSkipDuplicates() {
        when(importOrderRepository.findExistingOrderNos(anyCollection())).thenReturn(List.of("C001"));
        when(importOrderArchiveRepository.findExistingOrderNos(anyCollection())).thenReturn(List.of());

        List<OrderRow> rows =
                List.of(
                        row(2, "C001", "历史重复"),
                        row(3, "D001", "保留1"),
                        row(4, "D001", "文件重复"),
                        row(5, "D002", "保留2"));

        List<OrderRow> filtered = duplicateCheckService.filterForImport(rows, false);

        assertEquals(2, filtered.size());
        assertEquals(3, filtered.get(0).getSourceRowNum());
        assertEquals("D001", filtered.get(0).getOrderNo());
        assertEquals(5, filtered.get(1).getSourceRowNum());
    }

    @Test
    void filterForImport_shouldKeepAllWhenIncludeDuplicates() {
        List<OrderRow> rows = List.of(row(2, "E001", "商品1"), row(3, "E001", "商品2"));

        List<OrderRow> filtered = duplicateCheckService.filterForImport(rows, true);

        assertEquals(2, filtered.size());
        verify(importOrderRepository, never()).findExistingOrderNos(anyCollection());
    }

    private List<OrderRow> sampleRows() {
        return List.of(row(2, "X001", "商品"));
    }

    private ColumnMappingConfig mappingWithoutOrderNo() {
        ColumnMappingConfig mapping = new ColumnMappingConfig();
        ColumnMappingItem productName = new ColumnMappingItem();
        productName.setFieldKey(OrderFieldKey.PRODUCT_NAME);
        productName.setSourceIndex(0);
        productName.setEnabled(true);
        productName.setSortOrder(0);
        mapping.getItems().add(productName);
        return mapping;
    }

    private ColumnMappingConfig mappingWithOrderNo() {
        ColumnMappingConfig mapping = mappingWithoutOrderNo();
        ColumnMappingItem orderNo = new ColumnMappingItem();
        orderNo.setFieldKey(OrderFieldKey.ORDER_NO);
        orderNo.setSourceIndex(1);
        orderNo.setEnabled(true);
        orderNo.setSortOrder(1);
        mapping.getItems().add(orderNo);
        return mapping;
    }

    private OrderRow row(int sourceRowNum, String orderNo, String productName) {
        return OrderRow.builder()
                .sourceRowNum(sourceRowNum)
                .orderNo(orderNo)
                .productName(productName)
                .quantity(1)
                .build();
    }
}
