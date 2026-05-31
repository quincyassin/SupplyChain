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
import org.hibernate.annotations.CreationTimestamp;

/**
 * 导入分单订单明细（持久化到 import_order 表）
 *
 * @author huangxinsong
 */
@Entity
@Table(name = "import_order", indexes = {
        @Index(name = "idx_import_order_task_id", columnList = "task_id"),
        @Index(name = "idx_import_order_merchant", columnList = "merchant"),
        @Index(name = "idx_import_order_platform", columnList = "platform"),
        @Index(name = "idx_import_order_issue_date", columnList = "issue_date"),
        @Index(name = "idx_import_order_receipt_status", columnList = "receipt_status"),
        @Index(name = "idx_import_order_after_sales", columnList = "after_sales"),
        @Index(name = "idx_import_order_after_sales_status", columnList = "after_sales_status")
})
@Getter
@Setter
public class ImportOrder {

    /** 系统编号（10 位雪花 ID，主键） */
    @Id
    @Column(name = "system_no", length = 20, nullable = false)
    private String systemNo;

    /** 关联分单任务 ID */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 128)
    private String merchant;

    /** 是否已执行过按商家分单（与「未定义」展示无关） */
    @Column(name = "merchant_split", nullable = false)
    private Boolean merchantSplit = false;

    /** 导入时匹配的平台模板名称 */
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

    /** 备注（导入 Excel 映射） */
    @Column(length = 512)
    private String remark;

    /** 成本价 */
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    /** 供货价 */
    @Column(name = "supply_price", precision = 12, scale = 2)
    private BigDecimal supplyPrice;

    /** 回单状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false, length = 20)
    private ImportOrderReceiptStatus receiptStatus = ImportOrderReceiptStatus.PENDING;

    /** 物流单号 */
    @Column(name = "logistics_no", length = 128)
    private String logisticsNo;

    /** 物流公司 */
    @Column(name = "logistics_company", length = 128)
    private String logisticsCompany;

    /** 是否需售后 */
    @Column(name = "after_sales", nullable = false)
    private Boolean afterSales = false;

    /** 售后原因备注 */
    @Column(name = "after_sales_remark", length = 512)
    private String afterSalesRemark;

    /** 标记售后时间 */
    @Column(name = "after_sales_at")
    private LocalDateTime afterSalesAt;

    /** 售后状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "after_sales_status", nullable = false, length = 20)
    private AfterSalesStatus afterSalesStatus = AfterSalesStatus.NONE;

    /** 发单日期 */
    @Column(name = "issue_date", nullable = false)
    private LocalDateTime issueDate;

    /** Excel 原始行号 */
    @Column(name = "source_row_num")
    private Integer sourceRowNum;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
