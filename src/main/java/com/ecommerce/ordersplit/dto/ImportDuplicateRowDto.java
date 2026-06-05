package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.ImportDuplicateReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入重复订单行预览
 *
 * @author huangxinsong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportDuplicateRowDto {

    /** Excel 原始行号 */
    private int sourceRowNum;
    private String orderNo;
    private String productName;
    private String spec;
    private int quantity;
    private String receiver;
    private ImportDuplicateReason duplicateReason;
}
