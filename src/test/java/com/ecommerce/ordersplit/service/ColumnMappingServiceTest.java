package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ColumnMappingItemDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 列映射服务单元测试
 *
 * @author huangxinsong
 */
class ColumnMappingServiceTest {

    private ColumnMappingService service;

    @BeforeEach
    void setUp() {
        service = ColumnMappingTestFixtures.createColumnMappingService();
    }

    @Test
    void suggestMappingFromHeaders_shouldUseRealColumnIndex() {
        List<ExcelHeaderDto> headers =
            List.of(
                new ExcelHeaderDto(0, "订单编号"),
                new ExcelHeaderDto(1, "商品名称"),
                new ExcelHeaderDto(5, "规格"));
        ColumnMappingConfig config = service.suggestMappingFromHeaders(headers);

        assertTrue(
            config.getItems().stream()
                .anyMatch(
                    item -> item.getFieldKey() == OrderFieldKey.SKU
                        && item.getSourceIndex() == 5
                        && item.isEnabled()));
    }

    @Test
    void suggestMapping_shouldMatchByHeaderName() {
        List<String> headers = Arrays.asList("联系电话", "商家", "订单号", "金额", "数量", "单价", "SKU", "商品名称", "收货人", "收货地址");
        ColumnMappingConfig config = service.suggestMapping(headers);

        assertTrue(
                config.getItems().stream()
                        .noneMatch(item -> item.getFieldKey() == OrderFieldKey.MERCHANT));
        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.PRODUCT_NAME
                                        && item.getSourceIndex() == 7));
    }

    @Test
    void suggestMapping_shouldMatchReceiverAlias() {
        List<String> headers = Arrays.asList("收件人", "订单编号");
        ColumnMappingConfig config = service.suggestMapping(headers);

        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.RECEIVER
                                        && item.getSourceIndex() == 0));
    }

    @Test
    void suggestMapping_shouldMatchRemarkHeader() {
        List<String> headers = Arrays.asList("订单编号", "备注");
        ColumnMappingConfig config = service.suggestMapping(headers);

        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.REMARK
                                        && item.getSourceIndex() == 1
                                        && item.isEnabled()));
    }

    @Test
    void suggestMapping_shouldMatchAfterSalesRemarkHeader() {
        List<String> headers = Arrays.asList("订单编号", "售后原因");
        ColumnMappingConfig config = service.suggestMapping(headers);

        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.AFTER_SALES_REMARK
                                        && item.getSourceIndex() == 1
                                        && item.isEnabled()));
    }

    @Test
    void defaultMapping_shouldContainStandardFields() {
        ColumnMappingConfig config = service.defaultMapping();
        assertEquals(12, config.getItems().size());
        assertEquals(OrderFieldKey.ORDER_NO, config.getItems().get(0).getFieldKey());
        assertEquals(OrderFieldKey.PRODUCT_NAME, config.getItems().get(1).getFieldKey());
        assertEquals(OrderFieldKey.LOGISTICS_NO, config.getItems().get(2).getFieldKey());
        assertEquals(OrderFieldKey.LOGISTICS_COMPANY, config.getItems().get(3).getFieldKey());
        assertEquals(OrderFieldKey.RECEIVER, config.getItems().get(4).getFieldKey());
        assertEquals(OrderFieldKey.PHONE, config.getItems().get(5).getFieldKey());
        assertEquals(OrderFieldKey.ADDRESS, config.getItems().get(6).getFieldKey());
        assertTrue(
                config.getItems().stream()
                        .anyMatch(item -> item.getFieldKey() == OrderFieldKey.LOGISTICS_NO));
        assertTrue(
                config.getItems().stream()
                        .anyMatch(item -> item.getFieldKey() == OrderFieldKey.LOGISTICS_COMPANY));
    }

    @Test
    void suggestMapping_shouldMatchLogisticsHeaders() {
        List<String> headers = Arrays.asList("订单编号", "物流单号", "物流公司");
        ColumnMappingConfig config = service.suggestMapping(headers);

        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.LOGISTICS_NO
                                        && item.getSourceIndex() == 1));
        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.LOGISTICS_COMPANY
                                        && item.getSourceIndex() == 2));
    }

    @Test
    void parseMappingJson_forSplit_shouldIgnoreMerchantField() {
        String json = "[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0},"
                + "{\"fieldKey\":\"merchant\",\"sourceIndex\":1,\"enabled\":true,\"sortOrder\":1},"
                + "{\"fieldKey\":\"productName\",\"sourceIndex\":2,\"enabled\":true,\"sortOrder\":2}]";
        ColumnMappingConfig config = service.parseMappingJson(json, false);
        assertTrue(
                config.getItems().stream().noneMatch(item -> item.getFieldKey() == OrderFieldKey.MERCHANT));
        assertEquals(2, config.getItems().size());
    }

    @Test
    void parseMappingJson_shouldExposeJsonParseDetail() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.parseMappingJson("{invalid", false));
        assertTrue(ex.getMessage().contains("列映射 JSON 解析失败"));
    }

    @Test
    void suggestMapping_phoneShouldNotMapToReceiverColumn() {
        List<String> headers = List.of("订单编号", "收货人", "手机号");
        ColumnMappingConfig config = service.suggestMapping(headers);

        ColumnMappingItem receiver =
                config.getItems().stream()
                        .filter(item -> item.getFieldKey() == OrderFieldKey.RECEIVER)
                        .findFirst()
                        .orElseThrow();
        ColumnMappingItem phone =
                config.getItems().stream()
                        .filter(item -> item.getFieldKey() == OrderFieldKey.PHONE)
                        .findFirst()
                        .orElseThrow();

        assertEquals(1, receiver.getSourceIndex());
        assertTrue(receiver.isEnabled());
        assertEquals(2, phone.getSourceIndex());
        assertTrue(phone.isEnabled());
        assertTrue(
                config.getItems().stream()
                        .noneMatch(
                                item -> item.getFieldKey() == OrderFieldKey.PHONE
                                        && item.getSourceIndex() == receiver.getSourceIndex()));
    }

    @Test
    void suggestMapping_shouldNotDefaultUnmatchedFieldsToFirstColumn() {
        List<String> headers = List.of("订单编号");
        ColumnMappingConfig config = service.suggestMapping(headers);

        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.ORDER_NO
                                        && item.getSourceIndex() == 0
                                        && item.isEnabled()));
        assertTrue(
                config.getItems().stream()
                        .filter(item -> item.getFieldKey() != OrderFieldKey.ORDER_NO)
                        .allMatch(item -> item.getSourceIndex() < 0 && !item.isEnabled()));
    }

    @Test
    void mergePlatformMapping_shouldDisableUnmatchedFields() {
        List<ColumnMappingItemDto> saved = List.of(createMappingDto("orderNo", 0, true, 0));
        List<ExcelHeaderDto> headers = List.of(new ExcelHeaderDto(0, "订单编号"));

        List<ColumnMappingItemDto> merged = service.mergePlatformMapping(saved, headers);

        ColumnMappingItemDto productName =
                merged.stream()
                        .filter(item -> "productName".equals(item.getFieldKey()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(-1, productName.getSourceIndex().intValue());
        assertEquals(false, productName.getEnabled());
        assertTrue(
                merged.stream()
                        .filter(item -> "logisticsNo".equals(item.getFieldKey()))
                        .findFirst()
                        .map(item -> item.getSourceIndex() < 0 && !item.getEnabled())
                        .orElse(false));
    }

    @Test
    void mergePlatformMapping_shouldAppendMissingLogisticsFields() {
        List<ColumnMappingItemDto> saved =
                List.of(
                        createMappingDto("orderNo", 0, true, 0),
                        createMappingDto("productName", 1, true, 1));
        List<ExcelHeaderDto> headers =
            List.of(
                new ExcelHeaderDto(0, "订单编号"),
                new ExcelHeaderDto(1, "商品名称"),
                new ExcelHeaderDto(2, "物流单号"),
                new ExcelHeaderDto(3, "物流公司"));

        List<ColumnMappingItemDto> merged = service.mergePlatformMapping(saved, headers);

        assertEquals(12, merged.size());
        assertTrue(
                merged.stream()
                        .anyMatch(
                                item -> "logisticsNo".equals(item.getFieldKey())
                                        && item.getSourceIndex() == 2
                                        && Boolean.TRUE.equals(item.getEnabled())));
        assertTrue(
                merged.stream()
                        .anyMatch(
                                item -> "logisticsCompany".equals(item.getFieldKey())
                                        && item.getSourceIndex() == 3
                                        && Boolean.TRUE.equals(item.getEnabled())));
    }

    @Test
    void mergePlatformMapping_shouldPreserveManualMappingWhenHeaderNameDoesNotMatch() {
        List<ColumnMappingItemDto> saved =
                List.of(
                        createMappingDto("orderNo", 0, true, 0),
                        createMappingDto("productName", 1, true, 1),
                        createMappingDto("receiver", 2, true, 2));
        List<ExcelHeaderDto> headers =
                List.of(
                        new ExcelHeaderDto(0, "订单编号"),
                        new ExcelHeaderDto(1, "商品名称"),
                        new ExcelHeaderDto(2, "买家昵称"));

        List<ColumnMappingItemDto> merged = service.mergePlatformMapping(saved, headers);

        ColumnMappingItemDto receiver =
                merged.stream()
                        .filter(item -> "receiver".equals(item.getFieldKey()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, receiver.getSourceIndex().intValue());
        assertEquals(true, receiver.getEnabled());
    }

    @Test
    void mergePlatformMapping_shouldFallbackWhenSavedIndexPointsToWrongHeader() {
        List<ColumnMappingItemDto> saved =
                List.of(
                        createMappingDto("orderNo", 0, true, 0),
                        createMappingDto("sku", 1, true, 1));
        List<ExcelHeaderDto> headers =
                List.of(
                        new ExcelHeaderDto(0, "订单编号"),
                        new ExcelHeaderDto(1, "商品名称"),
                        new ExcelHeaderDto(2, "规格"));

        List<ColumnMappingItemDto> merged = service.mergePlatformMapping(saved, headers);

        ColumnMappingItemDto sku =
                merged.stream()
                        .filter(item -> "sku".equals(item.getFieldKey()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, sku.getSourceIndex().intValue());
        assertEquals(true, sku.getEnabled());
    }

    @Test
    void ensureLogisticsTemplateHeaders_shouldPrependMissingLogisticsColumns() {
        List<ExcelHeaderDto> headers =
                List.of(
                        new ExcelHeaderDto(0, "订单编号"),
                        new ExcelHeaderDto(1, "商品名称"));

        List<ExcelHeaderDto> enriched = service.ensureLogisticsTemplateHeaders(headers);

        assertEquals(4, enriched.size());
        assertEquals("物流公司", enriched.get(0).getHeaderName());
        assertEquals(0, enriched.get(0).getColumnIndex());
        assertEquals("物流单号", enriched.get(1).getHeaderName());
        assertEquals(1, enriched.get(1).getColumnIndex());
        assertEquals("订单编号", enriched.get(2).getHeaderName());
        assertEquals(2, enriched.get(2).getColumnIndex());
        assertEquals("商品名称", enriched.get(3).getHeaderName());
        assertEquals(3, enriched.get(3).getColumnIndex());
    }

    @Test
    void ensureLogisticsTemplateHeaders_shouldKeepExistingLogisticsAliases() {
        List<ExcelHeaderDto> headers =
                List.of(
                        new ExcelHeaderDto(0, "订单编号"),
                        new ExcelHeaderDto(1, "快递单号"),
                        new ExcelHeaderDto(5, "承运商"));

        List<ExcelHeaderDto> enriched = service.ensureLogisticsTemplateHeaders(headers);

        assertEquals(3, enriched.size());
    }

    @Test
    void templateHeaderNamesForImportMatch_shouldIgnoreAutoLogisticsWhenUploadHasNone() {
        List<ExcelHeaderDto> templateHeaders =
                List.of(
                        new ExcelHeaderDto(0, "订单编号"),
                        new ExcelHeaderDto(1, "商品名称"),
                        new ExcelHeaderDto(2, "物流单号"),
                        new ExcelHeaderDto(3, "物流公司"));

        List<String> matchNames =
                service.templateHeaderNamesForImportMatch(
                        templateHeaders, List.of("订单编号", "商品名称"));

        assertEquals(List.of("订单编号", "商品名称"), matchNames);
    }

    @Test
    void validateForDailyTable_shouldRequireProductName() {
        ColumnMappingItemDto orderNo = createMappingDto("orderNo", 0, true, 0);
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.fromDtos(List.of(orderNo), false));
        assertTrue(ex.getMessage().contains("商品名称"));
    }

    @Test
    void validateForDailyTable_shouldAcceptProductNameMapping() {
        List<ColumnMappingItemDto> dtos =
                List.of(
                        createMappingDto("orderNo", 0, true, 0),
                        createMappingDto("productName", 1, true, 1));
        ColumnMappingConfig config = service.fromDtos(dtos, false);
        assertTrue(
                config.getItems().stream()
                        .anyMatch(
                                item -> item.getFieldKey() == OrderFieldKey.PRODUCT_NAME
                                        && item.isEnabled()
                                        && item.getSourceIndex() == 1));
    }

    private ColumnMappingItemDto createMappingDto(
            String fieldKey, int sourceIndex, boolean enabled, int sortOrder) {
        ColumnMappingItemDto dto = new ColumnMappingItemDto();
        dto.setFieldKey(fieldKey);
        dto.setSourceIndex(sourceIndex);
        dto.setEnabled(enabled);
        dto.setSortOrder(sortOrder);
        return dto;
    }
}
