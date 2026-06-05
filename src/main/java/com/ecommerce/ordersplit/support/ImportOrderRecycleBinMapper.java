package com.ecommerce.ordersplit.support;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.entity.ImportOrderRecycleBin;

/**
 * 回收站订单与主表订单字段映射
 *
 * @author huangxinsong
 */
public final class ImportOrderRecycleBinMapper {

    private ImportOrderRecycleBinMapper() {}

    public static ImportOrder toImportOrder(ImportOrderRecycleBin recycleBin) {
        if (recycleBin == null) {
            return null;
        }
        ImportOrder order = new ImportOrder();
        order.setSystemNo(recycleBin.getSystemNo());
        order.setTaskId(recycleBin.getTaskId());
        order.setMerchant(recycleBin.getMerchant());
        order.setMerchantSplit(recycleBin.getMerchantSplit());
        order.setPlatform(recycleBin.getPlatform());
        order.setOrderNo(recycleBin.getOrderNo());
        order.setProductName(recycleBin.getProductName());
        order.setSpec(recycleBin.getSpec());
        order.setQuantity(recycleBin.getQuantity());
        order.setReceiver(recycleBin.getReceiver());
        order.setAddress(recycleBin.getAddress());
        order.setPhone(recycleBin.getPhone());
        order.setShippingFee(recycleBin.getShippingFee());
        order.setRemark(recycleBin.getRemark());
        order.setCostPrice(recycleBin.getCostPrice());
        order.setSupplyPrice(recycleBin.getSupplyPrice());
        order.setReceiptStatus(recycleBin.getReceiptStatus());
        order.setLogisticsNo(recycleBin.getLogisticsNo());
        order.setLogisticsCompany(recycleBin.getLogisticsCompany());
        order.setAfterSales(recycleBin.getAfterSales());
        order.setAfterSalesRemark(recycleBin.getAfterSalesRemark());
        order.setAfterSalesAt(recycleBin.getAfterSalesAt());
        order.setAfterSalesStatus(recycleBin.getAfterSalesStatus());
        order.setIssueDate(recycleBin.getIssueDate());
        order.setSourceRowNum(recycleBin.getSourceRowNum());
        order.setCreatedAt(recycleBin.getCreatedAt());
        return order;
    }
}
