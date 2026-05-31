package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ColumnMappingItemDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.dto.SavePlatformTemplateRequest;
import com.ecommerce.ordersplit.entity.PlatformMappingTemplate;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.repository.PlatformMappingTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台模板服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class PlatformMappingTemplateServiceTest {

  @Mock private PlatformMappingTemplateRepository templateRepository;

  private PlatformMappingTemplateService service;

  @BeforeEach
  void setUp() {
    ColumnMappingService columnMappingService = ColumnMappingTestFixtures.createColumnMappingService();
    service =
        new PlatformMappingTemplateService(
            templateRepository, columnMappingService, new ObjectMapper());
  }

  @Test
  void create_shouldPersistPlatformName() {
    when(templateRepository.existsByPlatform("淘宝")).thenReturn(false);
    when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.create("淘宝");

    ArgumentCaptor<PlatformMappingTemplate> captor =
        ArgumentCaptor.forClass(PlatformMappingTemplate.class);
    verify(templateRepository).save(captor.capture());
    assertEquals("淘宝", captor.getValue().getPlatform());
    assertEquals("[]", captor.getValue().getMappingJson());
  }

  @Test
  void save_shouldPersistPlatformTemplate() {
    when(templateRepository.findByPlatform("淘宝")).thenReturn(Optional.empty());
    when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SavePlatformTemplateRequest request = new SavePlatformTemplateRequest();
    request.setTemplateFileName("tpl.xlsx");
    request.setTemplateHeaders(
        List.of(new ExcelHeaderDto(0, "订单编号"), new ExcelHeaderDto(1, "商品名称")));
    ColumnMappingItemDto item = new ColumnMappingItemDto();
    item.setFieldKey("orderNo");
    item.setSourceIndex(0);
    item.setEnabled(true);
    item.setSortOrder(0);
    ColumnMappingItemDto nameItem = new ColumnMappingItemDto();
    nameItem.setFieldKey("productName");
    nameItem.setSourceIndex(1);
    nameItem.setEnabled(true);
    nameItem.setSortOrder(1);
    request.setMapping(List.of(item, nameItem));

    service.save("淘宝", request);

    ArgumentCaptor<PlatformMappingTemplate> captor =
        ArgumentCaptor.forClass(PlatformMappingTemplate.class);
    verify(templateRepository).save(captor.capture());
    assertEquals("淘宝", captor.getValue().getPlatform());
  }

  @Test
  void matchByHeaders_shouldPickBestTemplate() {
    PlatformMappingTemplate templateA = new PlatformMappingTemplate();
    templateA.setPlatform("淘宝");
    templateA.setTemplateHeadersJson(
        "[{\"columnIndex\":0,\"headerName\":\"订单编号\"},{\"columnIndex\":1,\"headerName\":\"商品名称\"}]");
    templateA.setMappingJson(
        "[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0},"
            + "{\"fieldKey\":\"productName\",\"sourceIndex\":1,\"enabled\":true,\"sortOrder\":1}]");

    PlatformMappingTemplate templateB = new PlatformMappingTemplate();
    templateB.setPlatform("拼多多");
    templateB.setTemplateHeadersJson(
        "[{\"columnIndex\":0,\"headerName\":\"货号\"},{\"columnIndex\":1,\"headerName\":\"尺码\"}]");
    templateB.setMappingJson(
        "[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0},"
            + "{\"fieldKey\":\"productName\",\"sourceIndex\":1,\"enabled\":true,\"sortOrder\":1}]");

    when(templateRepository.findAllByOrderByPlatformAsc()).thenReturn(List.of(templateA, templateB));
    when(templateRepository.findByPlatform("淘宝")).thenReturn(Optional.of(templateA));

    TemplateHeaderMatch match =
        service.matchByHeaders(
            List.of(
                new ExcelHeaderDto(0, "订单编号"),
                new ExcelHeaderDto(1, "商品名称"),
                new ExcelHeaderDto(2, "数量")));

    assertEquals("淘宝", match.platform());
    assertEquals(2, match.matchScore());
  }

  @Test
  void resolveMappingForHeaders_shouldKeepSpecColumnWhenColumnIndexIsNotSequential() {
    PlatformMappingTemplate template = new PlatformMappingTemplate();
    template.setPlatform("淘宝");
    template.setTemplateHeadersJson(
        "[{\"columnIndex\":0,\"headerName\":\"订单编号\"},"
            + "{\"columnIndex\":1,\"headerName\":\"商品名称\"},"
            + "{\"columnIndex\":5,\"headerName\":\"规格\"}]");
    template.setMappingJson(
        "[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0},"
            + "{\"fieldKey\":\"productName\",\"sourceIndex\":1,\"enabled\":true,\"sortOrder\":1},"
            + "{\"fieldKey\":\"sku\",\"sourceIndex\":5,\"enabled\":true,\"sortOrder\":2}]");

    when(templateRepository.findByPlatform("淘宝")).thenReturn(java.util.Optional.of(template));

    ColumnMappingConfig mapping =
        service.resolveMappingForHeaders(
            "淘宝",
            List.of(
                new ExcelHeaderDto(0, "订单编号"),
                new ExcelHeaderDto(1, "商品名称"),
                new ExcelHeaderDto(5, "规格")));

    ColumnMappingItem skuItem =
        mapping.getItems().stream()
            .filter(item -> item.getFieldKey() == OrderFieldKey.SKU)
            .findFirst()
            .orElseThrow();
    assertTrue(skuItem.isEnabled());
    assertEquals(5, skuItem.getSourceIndex());
  }

  @Test
  void resolveMappingForHeaders_shouldRematchSpecWhenUploadColumnOrderDiffers() {
    PlatformMappingTemplate template = new PlatformMappingTemplate();
    template.setPlatform("淘宝");
    template.setTemplateHeadersJson(
        "[{\"columnIndex\":0,\"headerName\":\"订单编号\"},"
            + "{\"columnIndex\":1,\"headerName\":\"商品名称\"},"
            + "{\"columnIndex\":2,\"headerName\":\"规格\"}]");
    template.setMappingJson(
        "[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0},"
            + "{\"fieldKey\":\"productName\",\"sourceIndex\":1,\"enabled\":true,\"sortOrder\":1},"
            + "{\"fieldKey\":\"sku\",\"sourceIndex\":2,\"enabled\":true,\"sortOrder\":2}]");

    when(templateRepository.findByPlatform("淘宝")).thenReturn(java.util.Optional.of(template));

    ColumnMappingConfig mapping =
        service.resolveMappingForHeaders(
            "淘宝",
            List.of(
                new ExcelHeaderDto(0, "订单编号"),
                new ExcelHeaderDto(1, "商品名称"),
                new ExcelHeaderDto(2, "数量"),
                new ExcelHeaderDto(3, "规格")));

    ColumnMappingItem skuItem =
        mapping.getItems().stream()
            .filter(item -> item.getFieldKey() == OrderFieldKey.SKU)
            .findFirst()
            .orElseThrow();
    assertTrue(skuItem.isEnabled());
    assertEquals(3, skuItem.getSourceIndex());
  }

  @Test
  void resolveMappingForHeaders_shouldRematchSpecWithNormalizedHeaderName() {
    PlatformMappingTemplate template = new PlatformMappingTemplate();
    template.setPlatform("淘宝");
    template.setTemplateHeadersJson(
        "[{\"columnIndex\":0,\"headerName\":\"订单编号\"},"
            + "{\"columnIndex\":1,\"headerName\":\"商品名称\"},"
            + "{\"columnIndex\":2,\"headerName\":\"SKU\"}]");
    template.setMappingJson(
        "[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0},"
            + "{\"fieldKey\":\"productName\",\"sourceIndex\":1,\"enabled\":true,\"sortOrder\":1},"
            + "{\"fieldKey\":\"sku\",\"sourceIndex\":2,\"enabled\":true,\"sortOrder\":2}]");

    when(templateRepository.findByPlatform("淘宝")).thenReturn(java.util.Optional.of(template));

    ColumnMappingConfig mapping =
        service.resolveMappingForHeaders(
            "淘宝",
            List.of(
                new ExcelHeaderDto(0, "订单编号"),
                new ExcelHeaderDto(1, "商品名称"),
                new ExcelHeaderDto(2, "sku")));

    ColumnMappingItem skuItem =
        mapping.getItems().stream()
            .filter(item -> item.getFieldKey() == OrderFieldKey.SKU)
            .findFirst()
            .orElseThrow();
    assertTrue(skuItem.isEnabled());
    assertEquals(2, skuItem.getSourceIndex());
  }

  @Test
  void matchByHeaders_shouldFailWhenNoTemplateMatches() {
    when(templateRepository.findAllByOrderByPlatformAsc()).thenReturn(List.of());

    assertThrows(BusinessException.class, () -> service.matchByHeaders(List.of(new ExcelHeaderDto(0, "订单编号"))));
  }

  @Test
  void matchByHeaders_shouldFailWhenMissingAnyTemplateHeader() {
    PlatformMappingTemplate templateA = new PlatformMappingTemplate();
    templateA.setPlatform("淘宝");
    templateA.setTemplateHeadersJson(
        "[{\"columnIndex\":0,\"headerName\":\"订单编号\"},{\"columnIndex\":1,\"headerName\":\"商品名称\"}]");
    templateA.setMappingJson("[]");

    when(templateRepository.findAllByOrderByPlatformAsc()).thenReturn(List.of(templateA));

    assertThrows(
        BusinessException.class,
        () -> service.matchByHeaders(List.of(new ExcelHeaderDto(0, "订单编号"), new ExcelHeaderDto(1, "数量"))));
  }

  @Test
  void matchByHeaders_shouldFailWhenOnlyPartialOverlapWithExistingTemplate() {
    PlatformMappingTemplate templateA = new PlatformMappingTemplate();
    templateA.setPlatform("淘宝");
    templateA.setTemplateHeadersJson(
        "[{\"columnIndex\":0,\"headerName\":\"订单编号\"},"
            + "{\"columnIndex\":1,\"headerName\":\"商品名称\"},"
            + "{\"columnIndex\":2,\"headerName\":\"收货人\"},"
            + "{\"columnIndex\":3,\"headerName\":\"收货电话\"},"
            + "{\"columnIndex\":4,\"headerName\":\"收货地址\"},"
            + "{\"columnIndex\":5,\"headerName\":\"数量\"},"
            + "{\"columnIndex\":6,\"headerName\":\"单价\"},"
            + "{\"columnIndex\":7,\"headerName\":\"买家备注\"}]");
    templateA.setMappingJson("[]");

    when(templateRepository.findAllByOrderByPlatformAsc()).thenReturn(List.of(templateA));

    assertThrows(
        BusinessException.class,
        () ->
            service.matchByHeaders(
                List.of(
                    new ExcelHeaderDto(0, "订单编号"),
                    new ExcelHeaderDto(1, "商品名称"),
                    new ExcelHeaderDto(2, "平台特有字段A"),
                    new ExcelHeaderDto(3, "平台特有字段B"))));
  }

  @Test
  void matchByHeaders_shouldFailWhenMultiplePlatformsScoreEqually() {
    String headersJson =
        "[{\"columnIndex\":0,\"headerName\":\"订单编号\"},"
            + "{\"columnIndex\":1,\"headerName\":\"商品名称\"},"
            + "{\"columnIndex\":2,\"headerName\":\"数量\"}]";
    String mappingJson =
        "[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0},"
            + "{\"fieldKey\":\"productName\",\"sourceIndex\":1,\"enabled\":true,\"sortOrder\":1},"
            + "{\"fieldKey\":\"quantity\",\"sourceIndex\":2,\"enabled\":true,\"sortOrder\":2}]";

    PlatformMappingTemplate templateA = new PlatformMappingTemplate();
    templateA.setPlatform("淘宝");
    templateA.setTemplateHeadersJson(headersJson);
    templateA.setMappingJson(mappingJson);

    PlatformMappingTemplate templateB = new PlatformMappingTemplate();
    templateB.setPlatform("拼多多");
    templateB.setTemplateHeadersJson(headersJson);
    templateB.setMappingJson(mappingJson);

    when(templateRepository.findAllByOrderByPlatformAsc())
        .thenReturn(List.of(templateA, templateB));
    when(templateRepository.findByPlatform("淘宝")).thenReturn(Optional.of(templateA));
    when(templateRepository.findByPlatform("拼多多")).thenReturn(Optional.of(templateB));

    assertThrows(
        BusinessException.class,
        () ->
            service.matchByHeaders(
                List.of(
                    new ExcelHeaderDto(0, "订单编号"),
                    new ExcelHeaderDto(1, "商品名称"),
                    new ExcelHeaderDto(2, "数量"))));
  }
}
