package com.ecommerce.ordersplit.entity;

import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 导入分单订单明细（回收站表 import_order_recycle_bin）
 *
 * @author huangxinsong
 */
@Entity
@Table(
        name = "import_order_recycle_bin",
        indexes = {
            @Index(name = "idx_import_order_recycle_bin_task_id", columnList = "task_id"),
            @Index(name = "idx_import_order_recycle_bin_merchant", columnList = "merchant"),
            @Index(name = "idx_import_order_recycle_bin_platform", columnList = "platform"),
            @Index(name = "idx_import_order_recycle_bin_issue_date", columnList = "issue_date"),
            @Index(name = "idx_import_order_recycle_bin_deleted_at", columnList = "deleted_at"),
            @Index(name = "idx_import_order_recycle_bin_receipt_status", columnList = "receipt_status"),
            @Index(name = "idx_import_order_recycle_bin_after_sales", columnList = "after_sales"),
            @Index(
                    name = "idx_import_order_recycle_bin_after_sales_status",
                    columnList = "after_sales_status")
        })
@Getter
@Setter
public class ImportOrderRecycleBin {

    @Id
    @Column(name = "system_no", length = 20, nullable = false)
    private String systemNo;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 128)
    private String merchant;

    @Column(name = "merchant_split", nullable = false)
    private Boolean merchantSplit = false;

    @Column(length = 128)
    private String platform;

    @Column(name = "order_no", length = 64)
    private String orderNo;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(length = 128)
    private String spec;

    private Integer quantity;

    @Column(length = 64)
    private String receiver;

    @Column(length = 512)
    private String address;

    @Column(length = 32)
    private String phone;

    @Column(name = "shipping_fee", precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(length = 512)
    private String remark;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "supply_price", precision = 12, scale = 2)
    private BigDecimal supplyPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false, length = 20)
    private ImportOrderReceiptStatus receiptStatus = ImportOrderReceiptStatus.PENDING;

    @Column(name = "logistics_no", length = 128)
    private String logisticsNo;

    @Column(name = "logistics_company", length = 128)
    private String logisticsCompany;

    @Column(name = "after_sales", nullable = false)
    private Boolean afterSales = false;

    @Column(name = "after_sales_remark", length = 512)
    private String afterSalesRemark;

    @Column(name = "after_sales_at")
    private LocalDateTime afterSalesAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_sales_status", nullable = false, length = 20)
    private AfterSalesStatus afterSalesStatus = AfterSalesStatus.NONE;

    @Column(name = "issue_date", nullable = false)
    private LocalDateTime issueDate;

    @Column(name = "source_row_num")
    private Integer sourceRowNum;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;
}
