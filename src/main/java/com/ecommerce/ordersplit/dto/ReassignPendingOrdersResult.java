package com.ecommerce.ordersplit.dto;

/**
 * 按最新商家关键字对订单重新匹配的结果
 *
 * @author huangxinsong
 */
public record ReassignPendingOrdersResult(
    /** 扫描的订单数（今日重匹配 / 未分单重匹配等场景） */
    int scannedOrderCount,
    /** 成功匹配到具体商家的订单数 */
    int matchedOrderCount,
    /** 仍未匹配关键字、保持「未定义」的订单数 */
    int stillPendingOrderCount) {}
