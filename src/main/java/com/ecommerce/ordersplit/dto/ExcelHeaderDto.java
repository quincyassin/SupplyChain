package com.ecommerce.ordersplit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Excel 表头列
 *
 * @author huangxinsong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExcelHeaderDto {

  private int columnIndex;
  private String headerName;
}
