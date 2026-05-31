package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import java.time.LocalDateTime;

/**
 * 导入订单列表查询筛选条件
 *
 * @author huangxinsong
 */
public record ImportOrderListFilter(
        LocalDateTime startInclusive,
        LocalDateTime endExclusive,
        String keyword,
        String platform,
        String merchant,
        ImportOrderReceiptStatus receiptStatus,
        Boolean afterSales,
        AfterSalesStatus afterSalesStatus) {}
