package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.BatchReceiptResponse;
import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.service.ReceiptBatchParser.ReceiptLine;
import com.ecommerce.ordersplit.util.LogisticsNoUtil;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导入订单回单维护
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ImportOrderReceiptService {

    private final ImportOrderRepository importOrderRepository;
    private final ImportOrderQueryService importOrderQueryService;

    @Transactional
    public BatchReceiptResponse batchUpdateReceipt(
            LocalDate startDate, LocalDate endDate, String content) {
        LocalDate normalizedStart = importOrderQueryService.requireRecentDate(startDate);
        LocalDate normalizedEnd = importOrderQueryService.requireRecentDate(endDate);
        if (normalizedStart.isAfter(normalizedEnd)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }

        List<ReceiptLine> lines = ReceiptBatchParser.parse(content);

        LocalDateTime start = normalizedStart.atStartOfDay();
        LocalDateTime end = normalizedEnd.plusDays(1).atStartOfDay();

        List<ImportOrder> rangeOrders =
                importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                start, end);
        Map<String, ImportOrder> ordersBySystemNo = new LinkedHashMap<>();
        for (ImportOrder order : rangeOrders) {
            String systemNo = SystemNoGenerator.matchKey(order);
            if (!systemNo.isBlank()) {
                ordersBySystemNo.put(SystemNoGenerator.normalize(systemNo), order);
            }
        }

        int updatedCount = 0;
        Set<String> notFoundSystemNos = new LinkedHashSet<>();
        for (ReceiptLine line : lines) {
            String systemNoKey = SystemNoGenerator.normalize(line.systemNo());
            ImportOrder order = ordersBySystemNo.get(systemNoKey);
            if (order == null) {
                notFoundSystemNos.add(line.systemNo());
                continue;
            }
            order.setReceiptStatus(ImportOrderReceiptStatus.RECEIPTED);
            order.setLogisticsNo(LogisticsNoUtil.normalize(line.logisticsNo()));
            order.setLogisticsCompany(line.logisticsCompany());
            updatedCount++;
        }

        if (updatedCount == 0) {
            throw new BusinessException("未匹配到任何订单，请确认系统单号在所选分单日期区间内");
        }

        SplitResultResponse refreshed =
                importOrderQueryService.listOrdersByDateRange(
                        normalizedStart, normalizedEnd, null);
        return new BatchReceiptResponse(
                updatedCount,
                lines.size(),
                notFoundSystemNos.size(),
                new ArrayList<>(notFoundSystemNos),
                refreshed);
    }
}
