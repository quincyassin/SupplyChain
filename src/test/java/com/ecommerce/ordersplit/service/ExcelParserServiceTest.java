package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.model.OrderRow;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Excel 解析服务单元测试
 *
 * @author huangxinsong
 */
class ExcelParserServiceTest {

    private ExcelParserService parserService;

    @BeforeEach
    void setUp() {
        parserService =
                new ExcelParserService(ColumnMappingTestFixtures.createColumnMappingService());
    }

    @Test
    void parse_shouldReadLogisticsFieldsWhenMapped() throws Exception {
        byte[] excelBytes = buildSampleExcel();
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "orders.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        excelBytes);
        ColumnMappingConfig mapping = buildLogisticsMapping();

        List<OrderRow> rows = parserService.parseForDailyTable(file, mapping);

        assertEquals(1, rows.size());
        OrderRow row = rows.get(0);
        assertEquals("SF1234567890", row.getLogisticsNo());
        assertEquals("顺丰", row.getLogisticsCompany());
    }

    private byte[] buildSampleExcel() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("商品名称");
            header.createCell(1).setCellValue("物流单号");
            header.createCell(2).setCellValue("物流公司");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("蓝牙耳机");
            data.createCell(1).setCellValue("SF1234567890");
            data.createCell(2).setCellValue("顺丰");
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private ColumnMappingConfig buildLogisticsMapping() {
        ColumnMappingConfig mapping = new ColumnMappingConfig();
        mapping.getItems()
                .addAll(
                        List.of(
                                mappingItem(OrderFieldKey.PRODUCT_NAME, 0, 0),
                                mappingItem(OrderFieldKey.LOGISTICS_NO, 1, 1),
                                mappingItem(OrderFieldKey.LOGISTICS_COMPANY, 2, 2)));
        return mapping;
    }

    private ColumnMappingItem mappingItem(OrderFieldKey fieldKey, int sourceIndex, int sortOrder) {
        ColumnMappingItem item = new ColumnMappingItem();
        item.setFieldKey(fieldKey);
        item.setSourceIndex(sourceIndex);
        item.setEnabled(true);
        item.setSortOrder(sortOrder);
        return item;
    }
}
