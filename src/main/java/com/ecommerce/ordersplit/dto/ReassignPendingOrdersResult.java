package com.ecommerce.ordersplit.dto;

/**
 * 按最新商家关键字对未分单订单重新匹配的结果
 *
 * @author huangxinsong
 */
public record ReassignPendingOrdersResult(
    /** 扫描的待分单订单数（商家为空、未定义或未匹配） */
    int scannedOrderCount,
    /** 成功匹配到具体商家的订单数 */
    int matchedOrderCount,
    /** 仍未匹配关键字、保持「未定义」的订单数 */
    int stillPendingOrderCount) {}
