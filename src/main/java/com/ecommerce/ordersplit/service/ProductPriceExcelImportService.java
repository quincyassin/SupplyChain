package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ProductPriceImportResult;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.util.ContentDispositionUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品价格 Excel 批量导入
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ProductPriceExcelImportService {

    private static final int MAX_IMPORT_ROWS = 5000;
    private static final String IMPORT_TEMPLATE_FILE_NAME = "商品价格导入模板.xlsx";
    private static final String[] IMPORT_TEMPLATE_HEADERS =
            {"平台", "商品名称", "规格", "成本价", "供货价"};
    private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.CHINA);

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("平台", "platform"),
            Map.entry("platform", "platform"),
            Map.entry("商品名称", "productName"),
            Map.entry("商品名", "productName"),
            Map.entry("productname", "productName"),
            Map.entry("规格", "spec"),
            Map.entry("sku", "spec"),
            Map.entry("成本价", "costPrice"),
            Map.entry("costprice", "costPrice"),
            Map.entry("供货价", "supplyPrice"),
            Map.entry("supplyprice", "supplyPrice"));

    private final ProductPriceService productPriceService;

    public ResponseEntity<Resource> buildImportTemplateResponse() {
        byte[] bytes = buildImportTemplateBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.attachment(IMPORT_TEMPLATE_FILE_NAME))
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(bytes));
    }

    byte[] buildImportTemplateBytes() {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("商品价格");
            Row headerRow = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < IMPORT_TEMPLATE_HEADERS.length; columnIndex++) {
                headerRow.createCell(columnIndex).setCellValue(IMPORT_TEMPLATE_HEADERS[columnIndex]);
                sheet.setColumnWidth(columnIndex, 16 * 256);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("生成导入模板失败：" + ex.getMessage());
        }
    }

    @Transactional
    public ProductPriceImportResult importFromExcel(MultipartFile file) {
        validateFile(file);
        int importedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BusinessException("Excel 文件中没有工作表");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessException("Excel 表头不能为空");
            }
            Map<String, Integer> columnIndex = resolveHeaderColumns(headerRow);
            requireHeader(columnIndex, "productName", "商品名称");

            int lastRowNum = sheet.getLastRowNum();
            int dataRows = Math.max(0, lastRowNum - sheet.getFirstRowNum());
            if (dataRows > MAX_IMPORT_ROWS) {
                throw new BusinessException("单次导入不能超过 " + MAX_IMPORT_ROWS + " 行");
            }

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, columnIndex)) {
                    continue;
                }
                int excelRowNo = rowIndex + 1;
                try {
                    String platform = readOptionalCell(row, columnIndex.get("platform"));
                    String productName = readRequiredCell(row, columnIndex.get("productName"));
                    String spec = readOptionalCell(row, columnIndex.get("spec"));
                    BigDecimal costPrice = readPriceCell(row, columnIndex.get("costPrice"), "成本价");
                    BigDecimal supplyPrice =
                            readPriceCell(row, columnIndex.get("supplyPrice"), "供货价");
                    if (costPrice == null && supplyPrice == null) {
                        throw new BusinessException("成本价和供货价至少填写一项");
                    }
                    productPriceService.upsertImportedRow(
                            platform, productName, spec, costPrice, supplyPrice);
                    importedCount++;
                } catch (BusinessException ex) {
                    skippedCount++;
                    errors.add("第 " + excelRowNo + " 行：" + ex.getMessage());
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 失败：" + ex.getMessage());
        }

        if (importedCount == 0) {
            throw new BusinessException(
                    errors.isEmpty() ? "未导入任何有效数据" : String.join("；", errors));
        }
        return ProductPriceImportResult.builder()
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .errors(errors)
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null
                || !(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 文件");
        }
    }

    private Map<String, Integer> resolveHeaderColumns(Row headerRow) {
        Map<String, Integer> columns = new HashMap<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            String header = normalizeHeader(readCellText(headerRow.getCell(i)));
            if (header.isEmpty()) {
                continue;
            }
            String field = HEADER_ALIASES.get(header);
            if (field != null && !columns.containsKey(field)) {
                columns.put(field, i);
            }
        }
        return columns;
    }

    private void requireHeader(Map<String, Integer> columns, String field, String label) {
        if (!columns.containsKey(field)) {
            throw new BusinessException("缺少必填表头：" + label);
        }
    }

    private boolean isBlankRow(Row row, Map<String, Integer> columnIndex) {
        for (Integer index : columnIndex.values()) {
            if (index == null) {
                continue;
            }
            if (!readCellText(row.getCell(index)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readRequiredCell(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            throw new BusinessException("缺少必填列");
        }
        String value = readCellText(row.getCell(columnIndex)).trim();
        if (value.isEmpty()) {
            throw new BusinessException("存在空值");
        }
        return value;
    }

    private String readOptionalCell(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return "";
        }
        return readCellText(row.getCell(columnIndex)).trim();
    }

    private BigDecimal readPriceCell(Row row, Integer columnIndex, String label) {
        if (columnIndex == null) {
            return null;
        }
        String text = readCellText(row.getCell(columnIndex)).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new BusinessException(label + "格式无效");
        }
    }

    private String readCellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return DATA_FORMATTER.formatCellValue(cell);
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
