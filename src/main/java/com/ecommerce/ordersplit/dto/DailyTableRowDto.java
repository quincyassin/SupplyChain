package com.ecommerce.ordersplit.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 当日发单表格行
 *
 * @author huangxinsong
 */
@Data
@Builder
public class DailyTableRowDto {

    /** 系统编号（主键，10 位雪花 ID） */
    private String systemNo;

    /** 导入时匹配的平台 */
    private String platform;

    /** 分单商家（可手动编辑，与商家配置无关） */
    private String merchant;

    /** 订单编号 */
    private String orderNo;

    /** 商品名称 */
    private String productName;

    /** 规格 */
    private String spec;

    /** 数量 */
    private Integer quantity;

    /** 收货人 */
    private String receiver;

    /** 收货地址 */
    private String address;

    /** 手机号 */
    private String phone;

    /** 运费 */
    private BigDecimal shippingFee;

    /** 备注 */
    private String remark;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 供货价 */
    private BigDecimal supplyPrice;

    /** 回单状态：PENDING / RECEIPTED */
    private String receiptStatus;

    /** 回单状态展示文案 */
    private String receiptStatusLabel;

    /** 物流单号 */
    private String logisticsNo;

    /** 物流公司 */
    private String logisticsCompany;

    /** 发单日期 */
    private String issueDate;

    /** 是否需售后 */
    private Boolean afterSales;

    /** 售后原因备注 */
    private String afterSalesRemark;

    /** 标记售后时间 */
    private String afterSalesAt;

    /** 售后状态：NONE / PENDING / COMPLETED */
    private String afterSalesStatus;

    /** 售后状态展示文案 */
    private String afterSalesStatusLabel;

    /** 移入回收站时间（仅回收站列表展示） */
    private String deletedAt;
}
