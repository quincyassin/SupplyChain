package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ImportDuplicatePreviewDto;
import com.ecommerce.ordersplit.dto.ImportDuplicateRowDto;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ImportDuplicateReason;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.model.OrderRow;
import com.ecommerce.ordersplit.repository.ImportOrderArchiveRepository;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导入订单编号重复检测
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ImportOrderDuplicateCheckService {

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderArchiveRepository importOrderArchiveRepository;

    public boolean isOrderNoMapped(ColumnMappingConfig mapping) {
        if (mapping == null) {
            return false;
        }
        return mapping.enabledItemsSorted().stream()
                .anyMatch(item -> item.getFieldKey() == OrderFieldKey.ORDER_NO);
    }

    @Transactional(readOnly = true)
    public ImportDuplicatePreviewDto buildPreview(List<OrderRow> rows, ColumnMappingConfig mapping) {
        if (rows == null) {
            rows = List.of();
        }
        if (!isOrderNoMapped(mapping)) {
            return ImportDuplicatePreviewDto.builder()
                    .orderNoMapped(false)
                    .totalRows(rows.size())
                    .duplicateRowCount(0)
                    .build();
        }
        DuplicateAnalysis analysis = analyze(rows);
        return ImportDuplicatePreviewDto.builder()
                .orderNoMapped(true)
                .totalRows(rows.size())
                .duplicateRowCount(analysis.duplicateRows().size())
                .duplicateOrderNos(new ArrayList<>(analysis.duplicateOrderNos()))
                .duplicateRows(analysis.duplicateRows())
                .build();
    }

    public List<OrderRow> filterForImport(List<OrderRow> rows, boolean includeDuplicateOrderNos) {
        if (rows == null || rows.isEmpty() || includeDuplicateOrderNos) {
            return rows == null ? List.of() : rows;
        }
        DuplicateAnalysis analysis = analyze(rows);
        if (analysis.skipSourceRowNums().isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> !analysis.skipSourceRowNums().contains(row.getSourceRowNum()))
                .toList();
    }

    private DuplicateAnalysis analyze(List<OrderRow> rows) {
        Set<String> candidateOrderNos = collectCandidateOrderNos(rows);
        Set<String> existingOrderNos = loadExistingOrderNos(candidateOrderNos);

        Map<String, Integer> firstFileOccurrence = new LinkedHashMap<>();
        List<ImportDuplicateRowDto> duplicateRows = new ArrayList<>();
        Set<String> duplicateOrderNos = new LinkedHashSet<>();
        Set<Integer> skipSourceRowNums = new HashSet<>();

        for (OrderRow row : rows) {
            String orderNo = normalizeOrderNo(row.getOrderNo());
            if (orderNo.isEmpty()) {
                continue;
            }

            boolean fileDuplicate = firstFileOccurrence.containsKey(orderNo);
            boolean databaseDuplicate = existingOrderNos.contains(orderNo);
            if (!fileDuplicate && !databaseDuplicate) {
                firstFileOccurrence.put(orderNo, row.getSourceRowNum());
                continue;
            }

            duplicateOrderNos.add(orderNo);
            skipSourceRowNums.add(row.getSourceRowNum());
            duplicateRows.add(toDuplicateRowDto(row, fileDuplicate ? ImportDuplicateReason.FILE : ImportDuplicateReason.DATABASE));
            if (!fileDuplicate) {
                firstFileOccurrence.put(orderNo, row.getSourceRowNum());
            }
        }

        return new DuplicateAnalysis(duplicateRows, duplicateOrderNos, skipSourceRowNums);
    }

    private Set<String> collectCandidateOrderNos(List<OrderRow> rows) {
        Set<String> orderNos = new LinkedHashSet<>();
        for (OrderRow row : rows) {
            String orderNo = normalizeOrderNo(row.getOrderNo());
            if (!orderNo.isEmpty()) {
                orderNos.add(orderNo);
            }
        }
        return orderNos;
    }

    private Set<String> loadExistingOrderNos(Set<String> candidateOrderNos) {
        if (candidateOrderNos.isEmpty()) {
            return Set.of();
        }
        Set<String> existing = new HashSet<>();
        existing.addAll(importOrderRepository.findExistingOrderNos(candidateOrderNos));
        existing.addAll(importOrderArchiveRepository.findExistingOrderNos(candidateOrderNos));
        return existing;
    }

    private ImportDuplicateRowDto toDuplicateRowDto(OrderRow row, ImportDuplicateReason reason) {
        return ImportDuplicateRowDto.builder()
                .sourceRowNum(row.getSourceRowNum())
                .orderNo(normalizeOrderNo(row.getOrderNo()))
                .productName(safeText(row.getProductName()))
                .spec(safeText(row.getSku()))
                .quantity(row.getQuantity() == null ? 0 : row.getQuantity())
                .receiver(safeText(row.getReceiver()))
                .duplicateReason(reason)
                .build();
    }

    private String normalizeOrderNo(String orderNo) {
        return orderNo == null ? "" : orderNo.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record DuplicateAnalysis(
            List<ImportDuplicateRowDto> duplicateRows,
            Set<String> duplicateOrderNos,
            Set<Integer> skipSourceRowNums) {}
}
