package com.ecommerce.ordersplit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 归档/恢复操作结果
 *
 * @author huangxinsong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportOrderArchiveOperationResultDto {

    /** 本次移动条数 */
    private int movedCount;

    /** 操作说明 */
    private String message;
}
