package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ColumnMappingItemDto;
import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.dto.PlatformExportTemplateDto;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 分单导出服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MerchantSplitExportServiceTest {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock private ImportOrderRepository importOrderRepository;
    @Mock private ImportOrderQueryService importOrderQueryService;
    @Mock private ExcelWriterService excelWriterService;
    @Mock private PlatformMappingTemplateService platformMappingTemplateService;
    @Mock private ExportSettingsService exportSettingsService;

    private MerchantSplitExportService service;

    @BeforeEach
    void setUp() throws Exception {
        java.nio.file.Path exportRoot =
                java.nio.file.Files.createTempDirectory("merchant-export-test");
        org.mockito.Mockito.when(exportSettingsService.getExportRootPath())
                .thenReturn(exportRoot);
        service =
                new MerchantSplitExportService(
                        importOrderRepository,
                        importOrderQueryService,
                        excelWriterService,
                        platformMappingTemplateService,
                        exportSettingsService);
    }

    @Test
    void buildSplitExportZipForOrders_shouldUseExportDateNotIssueDate() throws IOException {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        LocalDate yesterday = today.minusDays(1);
        ImportOrder order = new ImportOrder();
        order.setSystemNo("V1StGXR8Z5jdHi6B");
        order.setMerchant("商家A");
        order.setIssueDate(yesterday.atStartOfDay().plusHours(10));

        DailyTableRowDto row =
                DailyTableRowDto.builder()
                        .systemNo("V1StGXR8Z5jdHi6B")
                        .merchant("商家A")
                        .orderNo("O001")
                        .build();

        when(importOrderQueryService.requireRecentDate(today)).thenReturn(today);
        when(importOrderQueryService.toRowDto(order)).thenReturn(row);
        when(excelWriterService.writeMerchantDailyTable(anyString(), any()))
                .thenReturn(new byte[] {1, 2, 3});

        byte[] zipBytes = service.buildSplitExportZipForOrders(today, List.of(order));

        assertTrue(zipBytes.length > 0);
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes))) {
            var entry = zipInputStream.getNextEntry();
            assertTrue(entry != null);
            assertTrue(entry.getName().startsWith(today + "/分单/"));
            assertTrue(entry.getName().contains("商家A-" + today));
        }
    }

    @Test
    void buildMerchantExportZip_shouldContainMerchantExcel() throws IOException {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        ImportOrder order = new ImportOrder();
        order.setSystemNo("V1StGXR8Z5jdHi6B");
        order.setMerchant("商家A");
        order.setIssueDate(today.atStartOfDay().plusHours(10));

        DailyTableRowDto row =
                DailyTableRowDto.builder()
                        .systemNo("V1StGXR8Z5jdHi6B")
                        .merchant("商家A")
                        .orderNo("O001")
                        .build();

        when(importOrderQueryService.requireRecentDate(today)).thenReturn(today);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                any(), any()))
                .thenReturn(List.of(order));
        when(importOrderQueryService.toRowDto(order)).thenReturn(row);
        when(excelWriterService.writeMerchantDailyTable(anyString(), any()))
                .thenReturn(new byte[] {1, 2, 3});

        byte[] zipBytes = service.buildMerchantExportZip(today);

        assertTrue(zipBytes.length > 0);
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes))) {
            var entry = zipInputStream.getNextEntry();
            assertTrue(entry != null);
            assertTrue(entry.getName().startsWith(today + "/分单/"));
            assertTrue(entry.getName().endsWith(".xlsx"));
            assertTrue(entry.getName().contains("商家A-" + today));
        }
        assertEquals(1, service.countMerchantExports(today));
    }

    @Test
    void buildReceiptExportZip_shouldUsePlatformTemplate() throws IOException {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        ImportOrder order = new ImportOrder();
        order.setSystemNo("V1StGXR8Z5jdHi6B");
        order.setMerchant("商家A");
        order.setPlatform("客莱拿");
        order.setReceiptStatus(com.ecommerce.ordersplit.model.ImportOrderReceiptStatus.RECEIPTED);
        order.setIssueDate(today.atStartOfDay().plusHours(10));

        DailyTableRowDto row =
                DailyTableRowDto.builder()
                        .systemNo("V1StGXR8Z5jdHi6B")
                        .merchant("商家A")
                        .platform("客莱拿")
                        .orderNo("O001")
                        .logisticsNo("SF123")
                        .logisticsCompany("顺丰")
                        .build();

        PlatformExportTemplateDto template = buildReceiptTemplate();

        when(importOrderQueryService.requireRecentDate(today)).thenReturn(today);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                any(), any()))
                .thenReturn(List.of(order));
        when(importOrderQueryService.toRowDto(order)).thenReturn(row);
        when(platformMappingTemplateService.resolveExportTemplate("客莱拿")).thenReturn(template);
        when(excelWriterService.writeMerchantReceiptTable(
                        eq("客莱拿"), anyString(), any(), any(ColumnMappingConfig.class), any()))
                .thenReturn(new byte[] {4, 5, 6});

        byte[] zipBytes = service.buildReceiptExportZip(today, today);

        assertTrue(zipBytes.length > 0);
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes))) {
            var entry = zipInputStream.getNextEntry();
            assertTrue(entry != null);
            assertTrue(entry.getName().startsWith(today + "/回单/"));
            assertTrue(entry.getName().contains("客莱拿-" + today + ".xlsx"));
        }
        assertEquals(1, service.countReceiptExports(today, today));
    }

    @Test
    void buildReceiptExportZip_shouldFilterBySelectedPlatforms() throws IOException {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        ImportOrder kelainaOrder = new ImportOrder();
        kelainaOrder.setSystemNo("V1StGXR8Z5jdHi6B");
        kelainaOrder.setMerchant("商家A");
        kelainaOrder.setPlatform("客莱拿");
        kelainaOrder.setReceiptStatus(com.ecommerce.ordersplit.model.ImportOrderReceiptStatus.RECEIPTED);
        kelainaOrder.setIssueDate(today.atStartOfDay().plusHours(10));

        ImportOrder taobaoOrder = new ImportOrder();
        taobaoOrder.setSystemNo("AbCdEfGhIjKlMnOp");
        taobaoOrder.setMerchant("商家B");
        taobaoOrder.setPlatform("淘宝");
        taobaoOrder.setReceiptStatus(com.ecommerce.ordersplit.model.ImportOrderReceiptStatus.RECEIPTED);
        taobaoOrder.setIssueDate(today.atStartOfDay().plusHours(11));

        PlatformExportTemplateDto template = buildReceiptTemplate();

        when(importOrderQueryService.requireRecentDate(today)).thenReturn(today);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                any(), any()))
                .thenReturn(List.of(kelainaOrder, taobaoOrder));
        when(importOrderQueryService.toRowDto(kelainaOrder))
                .thenReturn(
                        DailyTableRowDto.builder()
                                .systemNo("V1StGXR8Z5jdHi6B")
                                .platform("客莱拿")
                                .orderNo("O001")
                                .build());
        when(platformMappingTemplateService.resolveExportTemplate("客莱拿")).thenReturn(template);
        when(excelWriterService.writeMerchantReceiptTable(
                        eq("客莱拿"), anyString(), any(), any(ColumnMappingConfig.class), any()))
                .thenReturn(new byte[] {4, 5, 6});

        byte[] zipBytes =
                service.buildReceiptExportZip(today, today, List.of("客莱拿"));

        assertTrue(zipBytes.length > 0);
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes))) {
            var entry = zipInputStream.getNextEntry();
            assertTrue(entry != null);
            assertTrue(entry.getName().contains("客莱拿-" + today + ".xlsx"));
            assertTrue(zipInputStream.getNextEntry() == null);
        }
    }

    @Test
    void buildReceiptExportZip_shouldMergeMerchantsByPlatform() throws IOException {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        ImportOrder orderA = new ImportOrder();
        orderA.setSystemNo("V1StGXR8Z5jdHi6B");
        orderA.setMerchant("商家A");
        orderA.setPlatform("客莱拿");
        orderA.setReceiptStatus(com.ecommerce.ordersplit.model.ImportOrderReceiptStatus.RECEIPTED);
        orderA.setIssueDate(today.atStartOfDay().plusHours(10));

        ImportOrder orderB = new ImportOrder();
        orderB.setSystemNo("AbCdEfGhIjKlMnOp");
        orderB.setMerchant("商家B");
        orderB.setPlatform("客莱拿");
        orderB.setReceiptStatus(com.ecommerce.ordersplit.model.ImportOrderReceiptStatus.RECEIPTED);
        orderB.setIssueDate(today.atStartOfDay().plusHours(11));

        DailyTableRowDto rowA =
                DailyTableRowDto.builder()
                        .systemNo("V1StGXR8Z5jdHi6B")
                        .merchant("商家A")
                        .platform("客莱拿")
                        .orderNo("O001")
                        .build();
        DailyTableRowDto rowB =
                DailyTableRowDto.builder()
                        .systemNo("AbCdEfGhIjKlMnOp")
                        .merchant("商家B")
                        .platform("客莱拿")
                        .orderNo("O002")
                        .build();

        PlatformExportTemplateDto template = buildReceiptTemplate();

        when(importOrderQueryService.requireRecentDate(today)).thenReturn(today);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                any(), any()))
                .thenReturn(List.of(orderA, orderB));
        when(importOrderQueryService.toRowDto(orderA)).thenReturn(rowA);
        when(importOrderQueryService.toRowDto(orderB)).thenReturn(rowB);
        when(platformMappingTemplateService.resolveExportTemplate("客莱拿")).thenReturn(template);

        assertEquals(1, service.countReceiptExports(today, today));
    }

    @Test
    void buildReceiptExportZip_shouldIncludePendingSplitMerchantWhenReceipted() throws IOException {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        ImportOrder order = new ImportOrder();
        order.setSystemNo("AbCdEfGhIjKlMnOp");
        order.setMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
        order.setPlatform("客莱拿");
        order.setReceiptStatus(com.ecommerce.ordersplit.model.ImportOrderReceiptStatus.RECEIPTED);
        order.setIssueDate(today.atStartOfDay().plusHours(10));

        DailyTableRowDto row =
                DailyTableRowDto.builder()
                        .systemNo("AbCdEfGhIjKlMnOp")
                        .merchant(MerchantConfigService.PENDING_SPLIT_MERCHANT)
                        .platform("客莱拿")
                        .orderNo("O002")
                        .logisticsNo("YT456")
                        .logisticsCompany("圆通")
                        .build();

        PlatformExportTemplateDto template = buildReceiptTemplate();

        when(importOrderQueryService.requireRecentDate(today)).thenReturn(today);
        when(importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                any(), any()))
                .thenReturn(List.of(order));
        when(importOrderQueryService.toRowDto(order)).thenReturn(row);
        when(platformMappingTemplateService.resolveExportTemplate("客莱拿")).thenReturn(template);

        assertEquals(1, service.countReceiptExports(today, today));
    }

    @Test
    void filterSplitExportOrders_shouldFilterByPlatformAndMerchant() {
        ImportOrder kelainaOrder = new ImportOrder();
        kelainaOrder.setSystemNo("V1StGXR8Z5jdHi6B");
        kelainaOrder.setMerchant("商家A");
        kelainaOrder.setPlatform("客莱拿");

        ImportOrder taobaoOrder = new ImportOrder();
        taobaoOrder.setSystemNo("AbCdEfGhIjKlMnOp");
        taobaoOrder.setMerchant("商家B");
        taobaoOrder.setPlatform("淘宝");

        List<ImportOrder> all = List.of(kelainaOrder, taobaoOrder);

        assertEquals(1, service.filterSplitExportOrders(all, List.of("客莱拿"), null).size());
        assertEquals(
                "V1StGXR8Z5jdHi6B",
                service.filterSplitExportOrders(all, List.of("客莱拿"), null).get(0).getSystemNo());
        assertEquals(1, service.filterSplitExportOrders(all, null, List.of("商家B")).size());
        assertEquals(2, service.filterSplitExportOrders(all, null, null).size());
    }

    private PlatformExportTemplateDto buildReceiptTemplate() {
        ColumnMappingService columnMappingService = ColumnMappingTestFixtures.createColumnMappingService();
        ColumnMappingItemDto orderNo = new ColumnMappingItemDto();
        orderNo.setFieldKey("orderNo");
        orderNo.setSourceIndex(0);
        orderNo.setEnabled(true);
        orderNo.setSortOrder(0);
        ColumnMappingItemDto productName = new ColumnMappingItemDto();
        productName.setFieldKey("productName");
        productName.setSourceIndex(1);
        productName.setEnabled(true);
        productName.setSortOrder(1);
        ColumnMappingItemDto logisticsNo = new ColumnMappingItemDto();
        logisticsNo.setFieldKey("logisticsNo");
        logisticsNo.setSourceIndex(2);
        logisticsNo.setEnabled(true);
        logisticsNo.setSortOrder(2);
        ColumnMappingItemDto logisticsCompany = new ColumnMappingItemDto();
        logisticsCompany.setFieldKey("logisticsCompany");
        logisticsCompany.setSourceIndex(3);
        logisticsCompany.setEnabled(true);
        logisticsCompany.setSortOrder(3);
        ColumnMappingConfig mapping =
                columnMappingService.fromDtos(
                        List.of(orderNo, productName, logisticsNo, logisticsCompany), false);
        List<ExcelHeaderDto> headers =
                List.of(
                        new ExcelHeaderDto(0, "订单编号"),
                        new ExcelHeaderDto(1, "商品名称"),
                        new ExcelHeaderDto(2, "物流单号"),
                        new ExcelHeaderDto(3, "物流公司"));
        return new PlatformExportTemplateDto("客莱拿", mapping, headers);
    }
}
