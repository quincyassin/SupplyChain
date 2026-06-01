package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.dto.ParsedImportExcel;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.model.OrderRow;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 解析服务（支持自定义列映射）
 *
 * @author huangxinsong
 */
@Service
public class ExcelParserService {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private final ColumnMappingService columnMappingService;

    public ExcelParserService(ColumnMappingService columnMappingService) {
        this.columnMappingService = columnMappingService;
    }

    public List<ExcelHeaderDto> readHeaders(MultipartFile file) {
        validateFile(file);
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = getFirstSheet(workbook);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException("Excel 缺少表头行");
            }
            List<ExcelHeaderDto> headers = new ArrayList<>();
            int lastCell = headerRow.getLastCellNum();
            for (int i = 0; i < lastCell; i++) {
                String name = getCellString(headerRow.getCell(i)).trim();
                if (!name.isEmpty()) {
                    headers.add(new ExcelHeaderDto(i, name));
                }
            }
            if (headers.isEmpty()) {
                throw new BusinessException("Excel 表头为空");
            }
            return headers;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 失败: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException("解析 Excel 失败: " + ex.getMessage());
        }
    }

    public List<OrderRow> parse(MultipartFile file, ColumnMappingConfig mapping) {
        return parse(file, mapping, true);
    }

    public List<OrderRow> parseForDailyTable(MultipartFile file, ColumnMappingConfig mapping) {
        return parse(file, mapping, false);
    }

    /**
     * 单次打开 Excel：读表头、按平台模板映射解析数据行
     */
    public ParsedImportExcel parseImportOnce(
            MultipartFile file,
            Function<List<ExcelHeaderDto>, TemplateHeaderMatch> matchResolver,
            boolean requireMerchant) {
        validateFile(file);
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = getFirstSheet(workbook);
            List<ExcelHeaderDto> headers = readHeaderDtos(sheet.getRow(0));
            if (headers.isEmpty()) {
                throw new BusinessException("Excel 表头为空");
            }
            TemplateHeaderMatch match = matchResolver.apply(headers);
            ColumnMappingConfig mapping = match.mapping();
            if (requireMerchant) {
                columnMappingService.validate(mapping);
            } else {
                columnMappingService.validateForDailyTable(mapping);
            }
            List<OrderRow> rows = parseDataRows(sheet, mapping);
            return new ParsedImportExcel(headers, match.platform(), rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 失败: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException("解析 Excel 失败: " + ex.getMessage());
        }
    }

    private List<OrderRow> parse(MultipartFile file, ColumnMappingConfig mapping, boolean requireMerchant) {
        validateFile(file);
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = getFirstSheet(workbook);
            ColumnMappingConfig effectiveMapping = resolveMapping(sheet, mapping);
            if (requireMerchant) {
                columnMappingService.validate(effectiveMapping);
            } else {
                columnMappingService.validateForDailyTable(effectiveMapping);
            }
            return parseDataRows(sheet, effectiveMapping);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("读取 Excel 失败: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException("解析 Excel 失败: " + ex.getMessage());
        }
    }

    private ColumnMappingConfig resolveMapping(Sheet sheet, ColumnMappingConfig mapping) {
        if (mapping != null && !mapping.getItems().isEmpty()) {
            return mapping;
        }
        return columnMappingService.suggestMappingFromHeaders(readHeaderDtos(sheet.getRow(0)));
    }

    private List<ExcelHeaderDto> readHeaderDtos(Row headerRow) {
        List<ExcelHeaderDto> headers = new ArrayList<>();
        if (headerRow == null) {
            return headers;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String name = getCellString(headerRow.getCell(i)).trim();
            if (!name.isEmpty()) {
                headers.add(new ExcelHeaderDto(i, name));
            }
        }
        return headers;
    }

    private List<String> readHeaderNames(Row headerRow) {
        List<String> names = new ArrayList<>();
        if (headerRow == null) {
            return names;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            names.add(getCellString(headerRow.getCell(i)).trim());
        }
        return names;
    }

    private List<OrderRow> parseDataRows(Sheet sheet, ColumnMappingConfig mapping) {
        Map<OrderFieldKey, Integer> fieldIndexMap = buildFieldIndexMap(mapping);
        List<OrderRow> rows = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isEmptyRow(row, fieldIndexMap)) {
                continue;
            }
            String merchant = getFieldString(row, fieldIndexMap, OrderFieldKey.MERCHANT);

            OrderRow orderRow = OrderRow.builder()
                    .orderNo(getFieldString(row, fieldIndexMap, OrderFieldKey.ORDER_NO))
                    .merchant(merchant)
                    .productName(getFieldString(row, fieldIndexMap, OrderFieldKey.PRODUCT_NAME))
                    .sku(getFieldString(row, fieldIndexMap, OrderFieldKey.SKU))
                    .quantity(parseIntegerField(row, fieldIndexMap, OrderFieldKey.QUANTITY, i + 1))
                    .unitPrice(parseDecimalField(row, fieldIndexMap, OrderFieldKey.UNIT_PRICE))
                    .amount(parseDecimalField(row, fieldIndexMap, OrderFieldKey.AMOUNT))
                    .receiver(getFieldString(row, fieldIndexMap, OrderFieldKey.RECEIVER))
                    .address(getFieldString(row, fieldIndexMap, OrderFieldKey.ADDRESS))
                    .phone(getFieldString(row, fieldIndexMap, OrderFieldKey.PHONE))
                    .shippingFee(parseDecimalField(row, fieldIndexMap, OrderFieldKey.SHIPPING_FEE))
                    .remark(getFieldString(row, fieldIndexMap, OrderFieldKey.REMARK))
                    .afterSalesRemark(
                            getFieldString(row, fieldIndexMap, OrderFieldKey.AFTER_SALES_REMARK))
                    .sourceRowNum(i + 1)
                    .build();
            fillAmountIfMissing(orderRow);
            ensureDefaultShippingFee(orderRow);
            rows.add(orderRow);
        }
        if (rows.isEmpty()) {
            throw new BusinessException("Excel 中没有有效数据行");
        }
        return rows;
    }

    private Map<OrderFieldKey, Integer> buildFieldIndexMap(ColumnMappingConfig mapping) {
        Map<OrderFieldKey, Integer> map = new HashMap<>();
        for (ColumnMappingItem item : mapping.enabledItemsSorted()) {
            map.put(item.getFieldKey(), item.getSourceIndex());
        }
        return map;
    }

    private String getFieldString(
            Row row, Map<OrderFieldKey, Integer> fieldIndexMap, OrderFieldKey fieldKey) {
        Integer index = fieldIndexMap.get(fieldKey);
        if (index == null) {
            return "";
        }
        return getCellString(row.getCell(index)).trim();
    }

    private Integer parseIntegerField(
            Row row, Map<OrderFieldKey, Integer> fieldIndexMap, OrderFieldKey fieldKey, int rowNum) {
        Integer index = fieldIndexMap.get(fieldKey);
        if (index == null) {
            return 0;
        }
        return parseInteger(row.getCell(index), rowNum, fieldKey.getLabel());
    }

    private BigDecimal parseDecimalField(
            Row row, Map<OrderFieldKey, Integer> fieldIndexMap, OrderFieldKey fieldKey) {
        Integer index = fieldIndexMap.get(fieldKey);
        if (index == null) {
            return BigDecimal.ZERO;
        }
        return parseDecimal(row.getCell(index));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 格式");
        }
    }

    private Sheet getFirstSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet == null || sheet.getPhysicalNumberOfRows() < 2) {
            throw new BusinessException("Excel 至少需要包含表头和一行数据");
        }
        return sheet;
    }

    private void fillAmountIfMissing(OrderRow row) {
        if (row.getAmount() == null || row.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            if (row.getUnitPrice() != null && row.getQuantity() != null) {
                row.setAmount(row.getUnitPrice().multiply(BigDecimal.valueOf(row.getQuantity())));
            } else {
                row.setAmount(BigDecimal.ZERO);
            }
        }
    }

    private void ensureDefaultShippingFee(OrderRow row) {
        if (row.getShippingFee() == null) {
            row.setShippingFee(BigDecimal.ZERO);
        }
    }

    private boolean isEmptyRow(Row row, Map<OrderFieldKey, Integer> fieldIndexMap) {
        Integer merchantIndex = fieldIndexMap.get(OrderFieldKey.MERCHANT);
        if (merchantIndex != null && !getCellString(row.getCell(merchantIndex)).trim().isEmpty()) {
            return false;
        }
        for (Integer index : fieldIndexMap.values()) {
            if (!getCellString(row.getCell(index)).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Integer parseInteger(Cell cell, int rowNum, String fieldName) {
        String text = getCellString(cell).trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new BusinessException("第 " + rowNum + " 行" + fieldName + "格式不正确");
        }
    }

    private BigDecimal parseDecimal(Cell cell) {
        String text = getCellString(cell).trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell);
    }
}
