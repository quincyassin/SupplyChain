package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.entity.ImportOrder;
import java.util.List;

/**
 * 待分单批量分单持久化结果
 *
 * @author huangxinsong
 */
public record AssignMerchantPersistenceResult(
        /** 本次处理的待分单条数（含未匹配仍保留待分单） */
        int processedCount,
        /** 未匹配关键字、仍保留为「待分单」的条数 */
        int unmatchedPendingCount,
        /** 本次处理并已保存的订单（供导出复用，避免二次查库） */
        List<ImportOrder> processedOrders) {}
