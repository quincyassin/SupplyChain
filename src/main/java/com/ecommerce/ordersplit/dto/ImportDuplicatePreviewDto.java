package com.ecommerce.ordersplit.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入订单编号重复预览
 *
 * @author huangxinsong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportDuplicatePreviewDto {

    /** 是否映射了订单编号列 */
    private boolean orderNoMapped;
    private int totalRows;
    private int duplicateRowCount;
    @Builder.Default
    private List<String> duplicateOrderNos = new ArrayList<>();
    @Builder.Default
    private List<ImportDuplicateRowDto> duplicateRows = new ArrayList<>();
}
