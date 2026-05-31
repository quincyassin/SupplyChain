package com.ecommerce.ordersplit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * 生成示例 Excel 供联调使用
 *
 * @author huangxinsong
 */
class SampleExcelGeneratorTest {

  private static final String[] HEADERS = {
      "订单编号", "商家", "名称", "规格", "数量", "单价", "金额", "收货人", "收货地址", "手机号", "运费"
  };

  private static final List<String[]> SAMPLE_ROWS =
      Arrays.asList(
          new String[] {
              "O20250526001", "旗舰店A", "蓝牙耳机", "黑色", "2", "99.00", "198.00", "张三",
              "北京市朝阳区", "13800000001", "12.00"
          },
          new String[] {
              "O20250526002", "旗舰店B", "手机壳", "透明", "1", "29.00", "29.00", "李四",
              "上海市浦东新区", "13800000002", "8.00"
          },
          new String[] {
              "O20250526003", "旗舰店A", "充电线", "1米", "3", "19.00", "57.00", "王五",
              "广州市天河区", "13800000003", "6.50"
          },
          new String[] {
              "O20250526004", "旗舰店A", "蓝牙耳机", "黑色", "1", "99.00", "99.00", "赵六",
              "深圳市南山区", "13800000004", "12.00"
          });

  @Test
  void generateSampleExcel() throws IOException {
    Path docsDir = Path.of("docs");
    Files.createDirectories(docsDir);
    Path output = docsDir.resolve("sample-orders.xlsx");

    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("订单");
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < HEADERS.length; i++) {
        headerRow.createCell(i).setCellValue(HEADERS[i]);
      }
      for (int r = 0; r < SAMPLE_ROWS.size(); r++) {
        Row row = sheet.createRow(r + 1);
        String[] values = SAMPLE_ROWS.get(r);
        for (int c = 0; c < values.length; c++) {
          row.createCell(c).setCellValue(values[c]);
        }
      }
      try (var out = Files.newOutputStream(output)) {
        workbook.write(out);
      }
    }
    System.out.println("示例文件已生成: " + output.toAbsolutePath());
  }
}
