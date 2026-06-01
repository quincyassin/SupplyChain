package com.ecommerce.ordersplit.support;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ImportOrderArchive;

/**
 * 归档订单与主表订单字段映射
 *
 * @author huangxinsong
 */
public final class ImportOrderArchiveMapper {

    private ImportOrderArchiveMapper() {}

    public static ImportOrder toImportOrder(ImportOrderArchive archive) {
        if (archive == null) {
            return null;
        }
        ImportOrder order = new ImportOrder();
        order.setSystemNo(archive.getSystemNo());
        order.setTaskId(archive.getTaskId());
        order.setMerchant(archive.getMerchant());
        order.setMerchantSplit(archive.getMerchantSplit());
        order.setPlatform(archive.getPlatform());
        order.setOrderNo(archive.getOrderNo());
        order.setProductName(archive.getProductName());
        order.setSpec(archive.getSpec());
        order.setQuantity(archive.getQuantity());
        order.setReceiver(archive.getReceiver());
        order.setAddress(archive.getAddress());
        order.setPhone(archive.getPhone());
        order.setShippingFee(archive.getShippingFee());
        order.setRemark(archive.getRemark());
        order.setCostPrice(archive.getCostPrice());
        order.setSupplyPrice(archive.getSupplyPrice());
        order.setReceiptStatus(archive.getReceiptStatus());
        order.setLogisticsNo(archive.getLogisticsNo());
        order.setLogisticsCompany(archive.getLogisticsCompany());
        order.setAfterSales(archive.getAfterSales());
        order.setAfterSalesRemark(archive.getAfterSalesRemark());
        order.setAfterSalesAt(archive.getAfterSalesAt());
        order.setAfterSalesStatus(archive.getAfterSalesStatus());
        order.setIssueDate(archive.getIssueDate());
        order.setSourceRowNum(archive.getSourceRowNum());
        order.setCreatedAt(archive.getCreatedAt());
        return order;
    }
}
