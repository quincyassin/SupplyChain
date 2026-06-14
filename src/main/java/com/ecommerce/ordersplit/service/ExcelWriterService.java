package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.model.OrderRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Excel 写出服务（按列映射配置输出表头与顺序）
 *
 * @author huangxinsong
 */
@Service
public class ExcelWriterService {

    private static final int STREAMING_ROW_WINDOW = 256;

    private final ColumnMappingService columnMappingService;

    private final ConcurrentHashMap<String, ReceiptWritePlan> receiptWritePlanCache =
            new ConcurrentHashMap<>();

    public ExcelWriterService(ColumnMappingService columnMappingService) {
        this.columnMappingService = columnMappingService;
    }

    private record ReceiptWritePlan(
            List<ExcelHeaderDto> sortedHeaders, Map<Integer, OrderFieldKey> fieldByColumnIndex) {}

    private static final String[] DISPLAY_TABLE_HEADERS = {
            "回单状态",
            "商家",
            "平台",
            "系统编号",
            "订单编号",
            "物流单号",
            "物流公司",
            "商品名称",
            "规格",
            "数量",
            "收货人",
            "收货人电话",
            "收货人地址",
            "运费",
            "售后原因",
            "备注",
            "分单日期"
    };

    /** 按商家分单导出：文件名/Sheet 已含商家，表格内不再重复展示商家、平台 */
    private static final String[] MERCHANT_SPLIT_EXPORT_HEADERS = {
            "回单状态",
            "系统编号",
            "物流公司",
            "物流单号",
            "订单编号",
            "商品名称",
            "规格",
            "数量",
            "收货人",
            "收货人电话",
            "收货人地址",
            "运费",
            "售后原因",
            "备注",
            "分单日期"
    };

    /** 售后订单导出：售后字段在前，按售后状态排序后写出 */
    private static final String[] AFTER_SALES_EXPORT_HEADERS = {
            "售后状态",
            "售后时间",
            "售后原因",
            "回单状态",
            "商家",
            "平台",
            "系统编号",
            "订单编号",
            "物流单号",
            "物流公司",
            "商品名称",
            "规格",
            "数量",
            "收货人",
            "收货人电话",
            "收货人地址",
            "运费",
            "备注",
            "分单日期"
    };

    /** 商家对账：不含平台/商家/供货价，含成本价、总价 */
    private static final String[] MERCHANT_RECONCILE_HEADERS = {
            "回单状态",
            "系统编号",
            "订单编号",
            "物流公司",
            "物流单号",
            "商品名称",
            "规格",
            "数量",
            "收货人",
            "收货人电话",
            "收货人地址",
            "运费",
            "成本价",
            "总价",
            "售后原因",
            "备注",
            "分单日期"
    };

    /** 平台对账：不含平台/商家/成本价，含供货价、总价 */
    private static final String[] PLATFORM_RECONCILE_HEADERS = {
            "回单状态",
            "系统编号",
            "订单编号",
            "物流公司",
            "物流单号",
            "商品名称",
            "规格",
            "数量",
            "收货人",
            "收货人电话",
            "收货人地址",
            "运费",
            "供货价",
            "总价",
            "售后原因",
            "备注",
            "分单日期"
    };

    private static final String AFTER_SALES_REMARK_HEADER = "售后原因";

    /**
     * 写出当日发单表格
     */
    public byte[] writeDailyTable(List<DailyTableRowDto> rows) throws IOException {
        try (SXSSFWorkbook workbook = createStreamingWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeDisplaySheet(workbook.createSheet("发单数据"), rows);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 按商家写出发单表格到本地文件（Sheet 标题为商家+日期）
     */
    public void writeMerchantDailyTable(
            Path outputPath, String sheetTitle, List<DailyTableRowDto> rows) throws IOException {
        byte[] fileBytes = writeMerchantDailyTable(sheetTitle, rows);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(
                outputPath,
                fileBytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * 按商家写出发单表格到内存（用于浏览器 ZIP 下载）
     */
    public byte[] writeMerchantDailyTable(String sheetTitle, List<DailyTableRowDto> rows)
            throws IOException {
        try (SXSSFWorkbook workbook = createStreamingWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeMerchantSplitExportSheet(
                    workbook.createSheet(sanitizeSheetName(sheetTitle)), rows);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 商家对账导出（不含平台/商家/供货价）
     */
    public byte[] writeMerchantReconcileTable(String sheetTitle, List<DailyTableRowDto> rows)
            throws IOException {
        return writeReconcileTable(sheetTitle, rows);
    }

    /**
     * 售后订单导出
     */
    public byte[] writeAfterSalesTable(List<DailyTableRowDto> rows) throws IOException {
        try (SXSSFWorkbook workbook = createStreamingWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeAfterSalesSheet(workbook.createSheet("售后订单"), rows);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] writeReconcileTable(String sheetTitle, List<DailyTableRowDto> rows)
            throws IOException {
        try (SXSSFWorkbook workbook = createStreamingWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeReconcileSheet(workbook.createSheet(sanitizeSheetName(sheetTitle)), rows);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 平台对账导出（系统固定表头，含供货价、总价）
     */
    public byte[] writePlatformReconcileTable(String sheetTitle, List<DailyTableRowDto> rows)
            throws IOException {
        try (SXSSFWorkbook workbook = createStreamingWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writePlatformReconcileSheet(workbook.createSheet(sanitizeSheetName(sheetTitle)), rows);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] writeMerchantReceiptTable(
            String platformKey,
            String sheetTitle,
            List<DailyTableRowDto> rows,
            ColumnMappingConfig mapping,
            List<ExcelHeaderDto> templateHeaders)
            throws IOException {
        ReceiptWritePlan writePlan = resolveReceiptWritePlan(platformKey, mapping, templateHeaders);
        try (SXSSFWorkbook workbook = createStreamingWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeReceiptTemplateSheet(
                    workbook.createSheet(sanitizeSheetName(sheetTitle)), rows, writePlan);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] writeMerchantReceiptTable(
            String sheetTitle,
            List<DailyTableRowDto> rows,
            ColumnMappingConfig mapping,
            List<ExcelHeaderDto> templateHeaders)
            throws IOException {
        return writeMerchantReceiptTable("default", sheetTitle, rows, mapping, templateHeaders);
    }

    /**
     * 按平台上传模板写出表格（仅使用已配置映射，未映射列留空）
     */
    public byte[] writePlatformTemplateTable(
            String sheetTitle,
            List<DailyTableRowDto> rows,
            ColumnMappingConfig mapping,
            List<ExcelHeaderDto> templateHeaders)
            throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writePlatformTemplateSheet(
                    workbook.createSheet(sanitizeSheetName(sheetTitle)), rows, mapping, templateHeaders);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 多平台模板合并为一个工作簿（每个平台一个 Sheet）
     */
    public byte[] writePlatformTemplateWorkbook(
            List<PlatformTemplateSheetExport> sheetExports) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Set<String> usedSheetNames = new HashSet<>();
            for (PlatformTemplateSheetExport sheetExport : sheetExports) {
                String sheetName = uniqueSheetName(sheetExport.sheetTitle(), usedSheetNames);
                writePlatformTemplateSheet(
                        workbook.createSheet(sheetName),
                        sheetExport.rows(),
                        sheetExport.mapping(),
                        sheetExport.templateHeaders());
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public record PlatformTemplateSheetExport(
            String sheetTitle,
            List<DailyTableRowDto> rows,
            ColumnMappingConfig mapping,
            List<ExcelHeaderDto> templateHeaders) {}

    public byte[] writeSingleSheet(
            List<OrderRow> rows, String sheetName, ColumnMappingConfig mapping) throws IOException {
        ColumnMappingConfig outputMapping = resolveOutputMapping(mapping);
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(sheetName));
            writeHeader(sheet, outputMapping);
            int rowIndex = 1;
            for (OrderRow orderRow : rows) {
                writeDataRow(sheet.createRow(rowIndex++), orderRow, outputMapping);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeDisplaySheet(Sheet sheet, List<DailyTableRowDto> rows) {
        writeTableSheet(sheet, rows, DISPLAY_TABLE_HEADERS, true);
    }

    private void writeMerchantSplitExportSheet(Sheet sheet, List<DailyTableRowDto> rows) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < MERCHANT_SPLIT_EXPORT_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(MERCHANT_SPLIT_EXPORT_HEADERS[i]);
        }
        int rowIndex = 1;
        for (DailyTableRowDto row : rows) {
            writeMerchantSplitDataRow(sheet.createRow(rowIndex++), row);
        }
    }

    private void writeMerchantSplitDataRow(Row dataRow, DailyTableRowDto row) {
        int col = 0;
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiptStatusLabel()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSystemNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsCompany()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getOrderNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getProductName()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSpec()));
        dataRow.createCell(col++)
                .setCellValue(row.getQuantity() == null ? 0 : row.getQuantity());
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiver()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getPhone()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAddress()));
        dataRow.createCell(col++)
                .setCellValue(
                        row.getShippingFee() == null ? 0 : row.getShippingFee().doubleValue());
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAfterSalesRemark()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getRemark()));
        dataRow.createCell(col).setCellValue(formatIssueDateOnly(row.getIssueDate()));
    }

    private void writeAfterSalesSheet(Sheet sheet, List<DailyTableRowDto> rows) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < AFTER_SALES_EXPORT_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(AFTER_SALES_EXPORT_HEADERS[i]);
        }
        int rowIndex = 1;
        for (DailyTableRowDto row : rows) {
            writeAfterSalesDataRow(sheet.createRow(rowIndex++), row);
        }
    }

    private void writeAfterSalesDataRow(Row dataRow, DailyTableRowDto row) {
        int col = 0;
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAfterSalesStatusLabel()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAfterSalesAt()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAfterSalesRemark()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiptStatusLabel()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getMerchant()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getPlatform()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSystemNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getOrderNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsCompany()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getProductName()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSpec()));
        dataRow.createCell(col++)
                .setCellValue(row.getQuantity() == null ? 0 : row.getQuantity());
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiver()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getPhone()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAddress()));
        dataRow.createCell(col++)
                .setCellValue(
                        row.getShippingFee() == null ? 0 : row.getShippingFee().doubleValue());
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getRemark()));
        dataRow.createCell(col).setCellValue(formatIssueDateOnly(row.getIssueDate()));
    }

    private void writeReconcileSheet(Sheet sheet, List<DailyTableRowDto> rows) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < MERCHANT_RECONCILE_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(MERCHANT_RECONCILE_HEADERS[i]);
        }
        int rowIndex = 1;
        for (DailyTableRowDto row : rows) {
            writeReconcileDataRow(sheet.createRow(rowIndex++), row);
        }
    }

    private void writeReconcileDataRow(Row dataRow, DailyTableRowDto row) {
        int col = 0;
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiptStatusLabel()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSystemNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getOrderNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsCompany()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getProductName()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSpec()));
        dataRow.createCell(col++)
                .setCellValue(row.getQuantity() == null ? 0 : row.getQuantity());
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiver()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getPhone()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAddress()));
        dataRow.createCell(col++)
                .setCellValue(
                        row.getShippingFee() == null ? 0 : row.getShippingFee().doubleValue());
        dataRow.createCell(col++).setCellValue(formatPriceCell(row.getCostPrice()));
        dataRow.createCell(col++)
                .setCellValue(
                        formatPriceCell(
                                calculateReconcileTotal(
                                        row.getCostPrice(), row.getQuantity(), row.getShippingFee())));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAfterSalesRemark()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getRemark()));
        dataRow.createCell(col).setCellValue(formatIssueDateOnly(row.getIssueDate()));
    }

    private void writePlatformReconcileSheet(Sheet sheet, List<DailyTableRowDto> rows) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < PLATFORM_RECONCILE_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(PLATFORM_RECONCILE_HEADERS[i]);
        }
        int rowIndex = 1;
        for (DailyTableRowDto row : rows) {
            writePlatformReconcileDataRow(sheet.createRow(rowIndex++), row);
        }
    }

    private void writePlatformReconcileDataRow(Row dataRow, DailyTableRowDto row) {
        int col = 0;
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiptStatusLabel()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSystemNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getOrderNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsCompany()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsNo()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getProductName()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSpec()));
        dataRow.createCell(col++)
                .setCellValue(row.getQuantity() == null ? 0 : row.getQuantity());
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiver()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getPhone()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAddress()));
        dataRow.createCell(col++)
                .setCellValue(
                        row.getShippingFee() == null ? 0 : row.getShippingFee().doubleValue());
        dataRow.createCell(col++).setCellValue(formatPriceCell(row.getSupplyPrice()));
        dataRow.createCell(col++)
                .setCellValue(
                        formatPriceCell(
                                calculateReconcileTotal(
                                        row.getSupplyPrice(),
                                        row.getQuantity(),
                                        row.getShippingFee())));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAfterSalesRemark()));
        dataRow.createCell(col++).setCellValue(nullToEmpty(row.getRemark()));
        dataRow.createCell(col).setCellValue(formatIssueDateOnly(row.getIssueDate()));
    }

    private int resolveMaxColumnIndex(List<ExcelHeaderDto> headers) {
        int maxIndex = -1;
        for (ExcelHeaderDto header : headers) {
            if (header.getColumnIndex() > maxIndex) {
                maxIndex = header.getColumnIndex();
            }
        }
        return maxIndex;
    }

    private BigDecimal calculateReconcileTotal(
            BigDecimal unitPrice, Integer quantity, BigDecimal shippingFee) {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        int qty = quantity == null ? 0 : quantity;
        BigDecimal shipping = shippingFee == null ? BigDecimal.ZERO : shippingFee;
        return price.multiply(BigDecimal.valueOf(qty)).add(shipping);
    }

    private void writeTableSheet(
            Sheet sheet,
            List<DailyTableRowDto> rows,
            String[] headers,
            boolean includeMerchantAndPlatform) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        int rowIndex = 1;
        for (DailyTableRowDto row : rows) {
            Row dataRow = sheet.createRow(rowIndex++);
            int col = 0;
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiptStatusLabel()));
            if (includeMerchantAndPlatform) {
                dataRow.createCell(col++).setCellValue(nullToEmpty(row.getMerchant()));
                dataRow.createCell(col++).setCellValue(nullToEmpty(row.getPlatform()));
            }
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSystemNo()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getOrderNo()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsNo()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getLogisticsCompany()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getProductName()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getSpec()));
            dataRow.createCell(col++)
                    .setCellValue(row.getQuantity() == null ? 0 : row.getQuantity());
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getReceiver()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getPhone()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAddress()));
            dataRow.createCell(col++)
                    .setCellValue(
                            row.getShippingFee() == null ? 0 : row.getShippingFee().doubleValue());
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getAfterSalesRemark()));
            dataRow.createCell(col++).setCellValue(nullToEmpty(row.getRemark()));
            dataRow.createCell(col).setCellValue(nullToEmpty(row.getIssueDate()));
        }
    }

    /**
     * 按上传模板表头位置写出数据行（严格按列映射，未映射列留空）
     */
    private void writePlatformTemplateSheet(
            Sheet sheet,
            List<DailyTableRowDto> rows,
            ColumnMappingConfig mapping,
            List<ExcelHeaderDto> templateHeaders) {
        List<ExcelHeaderDto> sortedHeaders =
                templateHeaders.stream()
                        .sorted(Comparator.comparingInt(ExcelHeaderDto::getColumnIndex))
                        .toList();
        Map<Integer, OrderFieldKey> fieldByColumnIndex = buildStrictFieldByColumnIndex(mapping);
        boolean appendAfterSalesRemark =
                shouldAppendAfterSalesRemarkColumn(rows, fieldByColumnIndex);
        int afterSalesRemarkColumnIndex = -1;
        if (appendAfterSalesRemark) {
            afterSalesRemarkColumnIndex = resolveMaxColumnIndex(sortedHeaders) + 1;
        }

        Row headerRow = sheet.createRow(0);
        for (ExcelHeaderDto header : sortedHeaders) {
            headerRow.createCell(header.getColumnIndex()).setCellValue(nullToEmpty(header.getHeaderName()));
        }
        if (appendAfterSalesRemark) {
            headerRow.createCell(afterSalesRemarkColumnIndex).setCellValue(AFTER_SALES_REMARK_HEADER);
        }

        int rowIndex = 1;
        for (DailyTableRowDto row : rows) {
            Row dataRow = sheet.createRow(rowIndex++);
            for (ExcelHeaderDto header : sortedHeaders) {
                OrderFieldKey fieldKey = fieldByColumnIndex.get(header.getColumnIndex());
                if (fieldKey == null) {
                    continue;
                }
                Cell cell = dataRow.createCell(header.getColumnIndex());
                setCellValueFromDailyRow(cell, row, fieldKey);
            }
            if (appendAfterSalesRemark) {
                dataRow.createCell(afterSalesRemarkColumnIndex)
                        .setCellValue(nullToEmpty(row.getAfterSalesRemark()));
            }
        }
    }

    private void writeReceiptTemplateSheet(
            Sheet sheet, List<DailyTableRowDto> rows, ReceiptWritePlan writePlan) {
        Row headerRow = sheet.createRow(0);
        for (ExcelHeaderDto header : writePlan.sortedHeaders()) {
            headerRow.createCell(header.getColumnIndex()).setCellValue(nullToEmpty(header.getHeaderName()));
        }

        int rowIndex = 1;
        for (DailyTableRowDto row : rows) {
            Row dataRow = sheet.createRow(rowIndex++);
            for (ExcelHeaderDto header : writePlan.sortedHeaders()) {
                OrderFieldKey fieldKey = writePlan.fieldByColumnIndex().get(header.getColumnIndex());
                if (fieldKey == null) {
                    continue;
                }
                Cell cell = dataRow.createCell(header.getColumnIndex());
                setCellValueFromDailyRow(cell, row, fieldKey);
            }
        }
    }

    private ReceiptWritePlan resolveReceiptWritePlan(
            String platformKey,
            ColumnMappingConfig mapping,
            List<ExcelHeaderDto> templateHeaders) {
        String cacheKey = platformKey == null ? "default" : platformKey.trim();
        return receiptWritePlanCache.computeIfAbsent(
                cacheKey, key -> buildReceiptWritePlan(mapping, templateHeaders));
    }

    private ReceiptWritePlan buildReceiptWritePlan(
            ColumnMappingConfig mapping, List<ExcelHeaderDto> templateHeaders) {
        List<ExcelHeaderDto> sortedHeaders =
                templateHeaders.stream()
                        .sorted(Comparator.comparingInt(ExcelHeaderDto::getColumnIndex))
                        .toList();
        Map<Integer, OrderFieldKey> fieldByColumnIndex =
                buildReceiptFieldByColumnIndex(mapping, sortedHeaders);
        return new ReceiptWritePlan(sortedHeaders, fieldByColumnIndex);
    }

    private SXSSFWorkbook createStreamingWorkbook() {
        SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_ROW_WINDOW);
        workbook.setCompressTempFiles(true);
        return workbook;
    }

    public void writeMerchantReceiptTable(
            Path outputPath,
            String sheetTitle,
            List<DailyTableRowDto> rows,
            ColumnMappingConfig mapping,
            List<ExcelHeaderDto> templateHeaders)
            throws IOException {
        byte[] fileBytes = writeMerchantReceiptTable(sheetTitle, rows, mapping, templateHeaders);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(
                outputPath,
                fileBytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private Map<Integer, OrderFieldKey> buildReceiptFieldByColumnIndex(
            ColumnMappingConfig mapping, List<ExcelHeaderDto> templateHeaders) {
        Map<Integer, OrderFieldKey> fieldByColumnIndex = new HashMap<>();
        if (mapping != null) {
            for (ColumnMappingItem item : mapping.getItems()) {
                if (item.getSourceIndex() < 0) {
                    continue;
                }
                boolean logisticsField =
                        item.getFieldKey() == OrderFieldKey.LOGISTICS_NO
                                || item.getFieldKey() == OrderFieldKey.LOGISTICS_COMPANY;
                if (item.isEnabled() || logisticsField) {
                    fieldByColumnIndex.put(item.getSourceIndex(), item.getFieldKey());
                }
            }
        }
        enrichLogisticsColumnsByHeader(fieldByColumnIndex, templateHeaders);
        return fieldByColumnIndex;
    }

    private Map<Integer, OrderFieldKey> buildStrictFieldByColumnIndex(ColumnMappingConfig mapping) {
        Map<Integer, OrderFieldKey> fieldByColumnIndex = new HashMap<>();
        if (mapping == null) {
            return fieldByColumnIndex;
        }
        for (ColumnMappingItem item : mapping.getItems()) {
            if (!item.isEnabled() || item.getSourceIndex() < 0) {
                continue;
            }
            fieldByColumnIndex.put(item.getSourceIndex(), item.getFieldKey());
        }
        return fieldByColumnIndex;
    }

    private void enrichLogisticsColumnsByHeader(
            Map<Integer, OrderFieldKey> fieldByColumnIndex, List<ExcelHeaderDto> templateHeaders) {
        if (!containsField(fieldByColumnIndex, OrderFieldKey.LOGISTICS_NO)) {
            resolveColumnByHeader(templateHeaders, OrderFieldKey.LOGISTICS_NO)
                    .ifPresent(index -> fieldByColumnIndex.put(index, OrderFieldKey.LOGISTICS_NO));
        }
        if (!containsField(fieldByColumnIndex, OrderFieldKey.LOGISTICS_COMPANY)) {
            resolveColumnByHeader(templateHeaders, OrderFieldKey.LOGISTICS_COMPANY)
                    .ifPresent(index -> fieldByColumnIndex.put(index, OrderFieldKey.LOGISTICS_COMPANY));
        }
    }

    private boolean containsField(Map<Integer, OrderFieldKey> fieldByColumnIndex, OrderFieldKey fieldKey) {
        return fieldByColumnIndex.containsValue(fieldKey);
    }

    private Optional<Integer> resolveColumnByHeader(
            List<ExcelHeaderDto> templateHeaders, OrderFieldKey fieldKey) {
        for (ExcelHeaderDto header : templateHeaders) {
            String headerName = header.getHeaderName();
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            if (matchesLogisticsHeader(headerName, fieldKey)) {
                return Optional.of(header.getColumnIndex());
            }
        }
        return Optional.empty();
    }

    private boolean matchesLogisticsHeader(String header, OrderFieldKey fieldKey) {
        String normalized = header == null ? "" : header.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        if (fieldKey == OrderFieldKey.LOGISTICS_NO) {
            return normalized.equals("物流单号")
                    || normalized.equals("快递单号")
                    || normalized.equals("运单号");
        }
        if (fieldKey == OrderFieldKey.LOGISTICS_COMPANY) {
            return normalized.equals("物流公司")
                    || normalized.equals("快递公司")
                    || normalized.equals("承运商");
        }
        return false;
    }

    private ColumnMappingConfig resolveOutputMapping(ColumnMappingConfig mapping) {
        if (mapping != null && !mapping.getItems().isEmpty()) {
            return mapping;
        }
        return columnMappingService.defaultMapping();
    }

    private void writeHeader(Sheet sheet, ColumnMappingConfig mapping) {
        Row headerRow = sheet.createRow(0);
        List<ColumnMappingItem> items = mapping.enabledItemsSorted();
        for (int i = 0; i < items.size(); i++) {
            headerRow.createCell(i).setCellValue(items.get(i).getFieldKey().getLabel());
        }
    }

    private void writeDataRow(Row row, OrderRow orderRow, ColumnMappingConfig mapping) {
        List<ColumnMappingItem> items = mapping.enabledItemsSorted();
        for (int i = 0; i < items.size(); i++) {
            Cell cell = row.createCell(i);
            setCellValueFromOrderRow(cell, orderRow, items.get(i).getFieldKey());
        }
    }

    private void setCellValueFromDailyRow(Cell cell, DailyTableRowDto row, OrderFieldKey fieldKey) {
        switch (fieldKey) {
            case ORDER_NO -> cell.setCellValue(nullToEmpty(row.getOrderNo()));
            case MERCHANT -> cell.setCellValue(nullToEmpty(row.getMerchant()));
            case PRODUCT_NAME -> cell.setCellValue(nullToEmpty(row.getProductName()));
            case SKU -> cell.setCellValue(nullToEmpty(row.getSpec()));
            case QUANTITY ->
                cell.setCellValue(row.getQuantity() == null ? 0 : row.getQuantity());
            case UNIT_PRICE, AMOUNT -> cell.setCellValue(0);
            case RECEIVER -> cell.setCellValue(nullToEmpty(row.getReceiver()));
            case ADDRESS -> cell.setCellValue(nullToEmpty(row.getAddress()));
            case PHONE -> cell.setCellValue(nullToEmpty(row.getPhone()));
            case SHIPPING_FEE ->
                cell.setCellValue(
                        row.getShippingFee() == null ? 0 : row.getShippingFee().doubleValue());
            case REMARK -> cell.setCellValue(nullToEmpty(row.getRemark()));
            case AFTER_SALES_REMARK -> cell.setCellValue(nullToEmpty(row.getAfterSalesRemark()));
            case LOGISTICS_NO -> cell.setCellValue(nullToEmpty(row.getLogisticsNo()));
            case LOGISTICS_COMPANY -> cell.setCellValue(nullToEmpty(row.getLogisticsCompany()));
            default -> cell.setCellValue("");
        }
    }

    private void setCellValueFromOrderRow(Cell cell, OrderRow orderRow, OrderFieldKey fieldKey) {
        switch (fieldKey) {
            case ORDER_NO -> cell.setCellValue(nullToEmpty(orderRow.getOrderNo()));
            case MERCHANT -> cell.setCellValue(nullToEmpty(orderRow.getMerchant()));
            case PRODUCT_NAME -> cell.setCellValue(nullToEmpty(orderRow.getProductName()));
            case SKU -> cell.setCellValue(nullToEmpty(orderRow.getSku()));
            case QUANTITY ->
                cell.setCellValue(orderRow.getQuantity() == null ? 0 : orderRow.getQuantity());
            case UNIT_PRICE ->
                cell.setCellValue(
                        orderRow.getUnitPrice() == null ? 0 : orderRow.getUnitPrice().doubleValue());
            case AMOUNT ->
                cell.setCellValue(orderRow.getAmount() == null ? 0 : orderRow.getAmount().doubleValue());
            case RECEIVER -> cell.setCellValue(nullToEmpty(orderRow.getReceiver()));
            case ADDRESS -> cell.setCellValue(nullToEmpty(orderRow.getAddress()));
            case PHONE -> cell.setCellValue(nullToEmpty(orderRow.getPhone()));
            case SHIPPING_FEE ->
                cell.setCellValue(
                        orderRow.getShippingFee() == null ? 0 : orderRow.getShippingFee().doubleValue());
            case REMARK -> cell.setCellValue(nullToEmpty(orderRow.getRemark()));
            case AFTER_SALES_REMARK ->
                cell.setCellValue(nullToEmpty(orderRow.getAfterSalesRemark()));
            case LOGISTICS_NO, LOGISTICS_COMPANY -> cell.setCellValue("");
            default -> cell.setCellValue("");
        }
    }

    private String sanitizeSheetName(String name) {
        String sanitized = name.replaceAll("[\\\\/*?\\[\\]:]", "_");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        if (sanitized.isBlank()) {
            return "Sheet";
        }
        return sanitized;
    }

    private String uniqueSheetName(String name, Set<String> usedSheetNames) {
        String base = sanitizeSheetName(name);
        if (usedSheetNames.add(base)) {
            return base;
        }
        int suffix = 2;
        while (true) {
            String candidate = sanitizeSheetName(base + "_" + suffix);
            if (candidate.length() > 31) {
                candidate = candidate.substring(0, 31);
            }
            if (usedSheetNames.add(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double formatPriceCell(BigDecimal price) {
        return price == null ? 0 : price.doubleValue();
    }

    private String formatIssueDateOnly(String issueDate) {
        if (issueDate == null || issueDate.isBlank()) {
            return "";
        }
        String trimmed = issueDate.trim();
        if (trimmed.length() >= 10) {
            return trimmed.substring(0, 10);
        }
        return trimmed;
    }

    private boolean shouldAppendAfterSalesRemarkColumn(
            List<DailyTableRowDto> rows, Map<Integer, OrderFieldKey> fieldByColumnIndex) {
        if (mappingContainsAfterSalesRemark(fieldByColumnIndex)) {
            return false;
        }
        return hasAfterSalesRemarkInRows(rows);
    }

    private boolean mappingContainsAfterSalesRemark(Map<Integer, OrderFieldKey> fieldByColumnIndex) {
        return fieldByColumnIndex.containsValue(OrderFieldKey.AFTER_SALES_REMARK);
    }

    private boolean hasAfterSalesRemarkInRows(List<DailyTableRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (DailyTableRowDto row : rows) {
            if (hasAfterSalesRemark(row)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAfterSalesRemark(DailyTableRowDto row) {
        if (row == null || row.getAfterSalesRemark() == null) {
            return false;
        }
        return !row.getAfterSalesRemark().isBlank();
    }
}
