package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 写出服务单元测试
 *
 * @author huangxinsong
 */
class ExcelWriterServiceTest {

  private ExcelWriterService service;

  @BeforeEach
  void setUp() {
    service = new ExcelWriterService(ColumnMappingTestFixtures.createColumnMappingService());
  }

  @Test
  void writeDailyTable_shouldWriteDisplayHeaders() throws Exception {
    List<DailyTableRowDto> rows =
        List.of(
            DailyTableRowDto.builder()
                .receiptStatusLabel("未回单")
                .merchant("商家A")
                .platform("淘宝")
                .systemNo("0123456789")
                .orderNo("O1")
                .productName("商品")
                .spec("规格")
                .quantity(1)
                .receiver("张三")
                .address("地址")
                .phone("13800000000")
                .shippingFee(new BigDecimal("10"))
                .issueDate("2026-05-28 12:00:00")
                .build());

    byte[] bytes = service.writeDailyTable(rows);

    assertTrue(bytes.length > 0);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      assertEquals("发单数据", workbook.getSheetAt(0).getSheetName());
      assertEquals("回单状态", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
      assertEquals("商家", workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
      assertEquals("平台", workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());
      assertEquals("系统编号", workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());
      assertEquals("未回单", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
      assertEquals("0123456789", workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
      assertEquals(2, workbook.getSheetAt(0).getPhysicalNumberOfRows());
    }
  }

  @Test
  void writeMerchantDailyTable_shouldOmitMerchantAndPlatformColumns() throws Exception {
    List<DailyTableRowDto> rows =
        List.of(
            DailyTableRowDto.builder()
                .receiptStatusLabel("已回单")
                .merchant("商家A")
                .platform("淘宝")
                .systemNo("9876543210")
                .logisticsCompany("顺丰")
                .logisticsNo("SF123456")
                .orderNo("O1")
                .productName("商品")
                .build());

    byte[] bytes = service.writeMerchantDailyTable("商家A2026-05-28", rows);

    assertTrue(bytes.length > 0);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      var headerRow = workbook.getSheetAt(0).getRow(0);
      assertEquals("商家A2026-05-28", workbook.getSheetAt(0).getSheetName());
      assertEquals("回单状态", headerRow.getCell(0).getStringCellValue());
      assertEquals("系统编号", headerRow.getCell(1).getStringCellValue());
      assertEquals("物流公司", headerRow.getCell(2).getStringCellValue());
      assertEquals("物流单号", headerRow.getCell(3).getStringCellValue());
      assertEquals("订单编号", headerRow.getCell(4).getStringCellValue());
      assertEquals(14, headerRow.getPhysicalNumberOfCells());
      var dataRow = workbook.getSheetAt(0).getRow(1);
      assertEquals("9876543210", dataRow.getCell(1).getStringCellValue());
      assertEquals("顺丰", dataRow.getCell(2).getStringCellValue());
      assertEquals("SF123456", dataRow.getCell(3).getStringCellValue());
      assertEquals("O1", dataRow.getCell(4).getStringCellValue());
    }
  }

  @Test
  void writePlatformTemplateTable_shouldLeaveUnmappedColumnsEmpty() throws Exception {
    ColumnMappingConfig mapping = new ColumnMappingConfig();
    ColumnMappingItem orderNoItem = new ColumnMappingItem();
    orderNoItem.setFieldKey(OrderFieldKey.ORDER_NO);
    orderNoItem.setSourceIndex(0);
    orderNoItem.setEnabled(true);
    orderNoItem.setSortOrder(0);
    mapping.getItems().add(orderNoItem);

    List<ExcelHeaderDto> templateHeaders =
        List.of(
            new ExcelHeaderDto(0, "平台订单号"),
            new ExcelHeaderDto(1, "商品名称"),
            new ExcelHeaderDto(2, "物流单号"));

    List<DailyTableRowDto> rows =
        List.of(
            DailyTableRowDto.builder()
                .orderNo("O1")
                .productName("商品A")
                .logisticsNo("SF123")
                .build());

    byte[] bytes =
        service.writePlatformTemplateTable("淘宝", rows, mapping, templateHeaders);

    try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      var dataRow = workbook.getSheetAt(0).getRow(1);
      assertEquals("O1", dataRow.getCell(0).getStringCellValue());
      assertEquals(null, dataRow.getCell(1));
      assertEquals(null, dataRow.getCell(2));
    }
  }

  @Test
  void writeMerchantReceiptTable_shouldUseUploadedTemplateHeaders() throws Exception {
    ColumnMappingConfig mapping = new ColumnMappingConfig();
    ColumnMappingItem orderNoItem = new ColumnMappingItem();
    orderNoItem.setFieldKey(OrderFieldKey.ORDER_NO);
    orderNoItem.setSourceIndex(0);
    orderNoItem.setEnabled(true);
    orderNoItem.setSortOrder(0);
    ColumnMappingItem logisticsNoItem = new ColumnMappingItem();
    logisticsNoItem.setFieldKey(OrderFieldKey.LOGISTICS_NO);
    logisticsNoItem.setSourceIndex(2);
    logisticsNoItem.setEnabled(true);
    logisticsNoItem.setSortOrder(1);
    ColumnMappingItem logisticsCompanyItem = new ColumnMappingItem();
    logisticsCompanyItem.setFieldKey(OrderFieldKey.LOGISTICS_COMPANY);
    logisticsCompanyItem.setSourceIndex(4);
    logisticsCompanyItem.setEnabled(true);
    logisticsCompanyItem.setSortOrder(2);
    mapping.getItems().add(orderNoItem);
    mapping.getItems().add(logisticsNoItem);
    mapping.getItems().add(logisticsCompanyItem);

    List<ExcelHeaderDto> templateHeaders =
        List.of(
            new ExcelHeaderDto(0, "平台订单号"),
            new ExcelHeaderDto(1, "商品名称"),
            new ExcelHeaderDto(2, "快递单号"),
            new ExcelHeaderDto(3, "收货人"),
            new ExcelHeaderDto(4, "快递公司"));

    List<DailyTableRowDto> rows =
        List.of(
            DailyTableRowDto.builder()
                .orderNo("O1")
                .logisticsNo("SF123456")
                .logisticsCompany("顺丰")
                .build());

    byte[] bytes =
        service.writeMerchantReceiptTable("商家A2026-05-28-回单", rows, mapping, templateHeaders);

    assertTrue(bytes.length > 0);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      var headerRow = workbook.getSheetAt(0).getRow(0);
      assertEquals("平台订单号", headerRow.getCell(0).getStringCellValue());
      assertEquals("快递单号", headerRow.getCell(2).getStringCellValue());
      assertEquals("快递公司", headerRow.getCell(4).getStringCellValue());
      var dataRow = workbook.getSheetAt(0).getRow(1);
      assertEquals("O1", dataRow.getCell(0).getStringCellValue());
      assertEquals("SF123456", dataRow.getCell(2).getStringCellValue());
      assertEquals("顺丰", dataRow.getCell(4).getStringCellValue());
    }
  }

  @Test
  void writeMerchantReceiptTable_shouldExportDisabledLogisticsByHeaderMatch() throws Exception {
    ColumnMappingConfig mapping = new ColumnMappingConfig();
    ColumnMappingItem orderNoItem = new ColumnMappingItem();
    orderNoItem.setFieldKey(OrderFieldKey.ORDER_NO);
    orderNoItem.setSourceIndex(0);
    orderNoItem.setEnabled(true);
    orderNoItem.setSortOrder(0);
    ColumnMappingItem logisticsNoItem = new ColumnMappingItem();
    logisticsNoItem.setFieldKey(OrderFieldKey.LOGISTICS_NO);
    logisticsNoItem.setSourceIndex(2);
    logisticsNoItem.setEnabled(false);
    logisticsNoItem.setSortOrder(1);
    mapping.getItems().add(orderNoItem);
    mapping.getItems().add(logisticsNoItem);

    List<ExcelHeaderDto> templateHeaders =
        List.of(
            new ExcelHeaderDto(0, "订单编号"),
            new ExcelHeaderDto(1, "商品名称"),
            new ExcelHeaderDto(2, "物流单号"),
            new ExcelHeaderDto(3, "物流公司"));

    List<DailyTableRowDto> rows =
        List.of(
            DailyTableRowDto.builder()
                .orderNo("O1")
                .logisticsNo("YT999")
                .logisticsCompany("圆通")
                .build());

    byte[] bytes =
        service.writeMerchantReceiptTable("商家A2026-05-28-回单", rows, mapping, templateHeaders);

    try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      var dataRow = workbook.getSheetAt(0).getRow(1);
      assertEquals("YT999", dataRow.getCell(2).getStringCellValue());
      assertEquals("圆通", dataRow.getCell(3).getStringCellValue());
    }
  }

  @Test
  void writeMerchantReconcileTable_shouldIncludeCostPriceAndOmitMerchantPlatformAndSupplyPrice()
      throws Exception {
    List<DailyTableRowDto> rows =
        List.of(
            DailyTableRowDto.builder()
                .receiptStatusLabel("已回单")
                .merchant("商家A")
                .platform("淘宝")
                .systemNo("9876543210")
                .logisticsCompany("顺丰")
                .logisticsNo("SF123456")
                .orderNo("O1")
                .productName("商品")
                .quantity(2)
                .shippingFee(new BigDecimal("8"))
                .costPrice(new BigDecimal("12.5"))
                .supplyPrice(new BigDecimal("15"))
                .issueDate("2026-05-28 12:00:00")
                .build());

    byte[] bytes = service.writeMerchantReconcileTable("商家A对账", rows);

    try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      var headerRow = workbook.getSheetAt(0).getRow(0);
      assertEquals("成本价", headerRow.getCell(12).getStringCellValue());
      assertEquals("总价", headerRow.getCell(13).getStringCellValue());
      assertEquals(16, headerRow.getPhysicalNumberOfCells());
      var dataRow = workbook.getSheetAt(0).getRow(1);
      assertEquals(12.5, dataRow.getCell(12).getNumericCellValue(), 0.001);
      assertEquals(33.0, dataRow.getCell(13).getNumericCellValue(), 0.001);
      assertEquals("2026-05-28", dataRow.getCell(15).getStringCellValue());
    }
  }

  @Test
  void writePlatformReconcileTable_shouldAppendSupplyPriceAndTotalColumns() throws Exception {
    ColumnMappingConfig mapping = new ColumnMappingConfig();
    ColumnMappingItem orderNoItem = new ColumnMappingItem();
    orderNoItem.setFieldKey(OrderFieldKey.ORDER_NO);
    orderNoItem.setSourceIndex(0);
    orderNoItem.setEnabled(true);
    orderNoItem.setSortOrder(0);
    ColumnMappingItem quantityItem = new ColumnMappingItem();
    quantityItem.setFieldKey(OrderFieldKey.QUANTITY);
    quantityItem.setSourceIndex(1);
    quantityItem.setEnabled(true);
    quantityItem.setSortOrder(1);
    mapping.getItems().add(orderNoItem);
    mapping.getItems().add(quantityItem);

    List<ExcelHeaderDto> templateHeaders =
        List.of(new ExcelHeaderDto(0, "订单编号"), new ExcelHeaderDto(1, "数量"));

    List<DailyTableRowDto> rows =
        List.of(
            DailyTableRowDto.builder()
                .orderNo("O1")
                .quantity(3)
                .shippingFee(new BigDecimal("5"))
                .supplyPrice(new BigDecimal("10"))
                .build());

    byte[] bytes =
        service.writePlatformReconcileTable("平台A对账", rows, mapping, templateHeaders);

    try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      var headerRow = workbook.getSheetAt(0).getRow(0);
      assertEquals("供货价", headerRow.getCell(2).getStringCellValue());
      assertEquals("总价", headerRow.getCell(3).getStringCellValue());
      var dataRow = workbook.getSheetAt(0).getRow(1);
      assertEquals(10.0, dataRow.getCell(2).getNumericCellValue(), 0.001);
      assertEquals(35.0, dataRow.getCell(3).getNumericCellValue(), 0.001);
    }
  }
}
