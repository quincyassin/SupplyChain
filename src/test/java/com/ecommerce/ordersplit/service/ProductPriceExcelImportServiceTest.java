package com.ecommerce.ordersplit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 商品价格 Excel 导入服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class ProductPriceExcelImportServiceTest {

    @Mock private ProductPriceService productPriceService;
    @Mock private OrderProductCatalogService orderProductCatalogService;

    @InjectMocks private ProductPriceExcelImportService productPriceExcelImportService;

    private DataFormatter dataFormatter;

    @BeforeEach
    void setUp() {
        dataFormatter = new DataFormatter();
    }

    @Test
    void buildImportTemplateBytes_shouldContainExpectedHeaders() throws Exception {
        byte[] bytes = productPriceExcelImportService.buildImportTemplateBytes();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("商品价格");

            Row headerRow = sheet.getRow(0);
            assertThat(readCell(headerRow, 0)).isEqualTo("平台");
            assertThat(readCell(headerRow, 1)).isEqualTo("商品名称");
            assertThat(readCell(headerRow, 2)).isEqualTo("规格");
            assertThat(readCell(headerRow, 3)).isEqualTo("成本价");
            assertThat(readCell(headerRow, 4)).isEqualTo("供货价");
        }
    }

    private String readCell(Row row, int columnIndex) {
        return dataFormatter.formatCellValue(row.getCell(columnIndex));
    }
}
