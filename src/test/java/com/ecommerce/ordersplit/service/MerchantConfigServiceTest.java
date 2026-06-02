package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ReassignPendingOrdersResult;
import com.ecommerce.ordersplit.dto.SaveMerchantConfigRequest;
import com.ecommerce.ordersplit.entity.MerchantConfig;
import com.ecommerce.ordersplit.model.MerchantConfigVisibility;
import com.ecommerce.ordersplit.repository.MerchantConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ecommerce.ordersplit.exception.BusinessException;

/**
 * 商家配置服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class MerchantConfigServiceTest {

  @Mock private MerchantConfigRepository merchantConfigRepository;

  @Mock private ImportOrderPersistenceService importOrderPersistenceService;

  private MerchantConfigService service;

  @BeforeEach
  void setUp() {
    service =
        new MerchantConfigService(
            merchantConfigRepository, importOrderPersistenceService, new ObjectMapper());
  }

  @Test
  void resolveByProductName_shouldUseLongestKeyword() {
    MerchantConfig configA = new MerchantConfig();
    configA.setId(1L);
    configA.setName("商家A");
    configA.setKeywordsJson("[\"耐克\",\"耐克鞋\"]");

    MerchantConfig configB = new MerchantConfig();
    configB.setId(2L);
    configB.setName("商家B");
    configB.setKeywordsJson("[\"阿迪\"]");

    when(merchantConfigRepository.findAllByOrderByNameAsc())
        .thenReturn(List.of(configA, configB));

    assertEquals("商家A", service.resolveByProductName("新款耐克鞋 Air"));
    assertEquals("商家B", service.resolveByProductName("阿迪经典款"));
    assertEquals(
        MerchantConfigService.UNMATCHED_MERCHANT_NAME,
        service.resolveByProductName("未知品牌卫衣"));
  }

  @Test
  void create_shouldPersistKeywords() {
    when(merchantConfigRepository.findByName("商家A")).thenReturn(Optional.empty());
    when(merchantConfigRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            inv -> {
              MerchantConfig entity = inv.getArgument(0);
              entity.setId(10L);
              return entity;
            });
    when(importOrderPersistenceService.reassignAllPendingOrders())
        .thenReturn(new ReassignPendingOrdersResult(5, 3, 2));

    SaveMerchantConfigRequest request = new SaveMerchantConfigRequest();
    request.setName("商家A");
    request.setKeywords(List.of("耐克", "AJ"));

    var dto = service.create(request);
    assertEquals("商家A", dto.getName());
    assertEquals(2, dto.getKeywords().size());
    assertEquals(5, dto.getReassignedScannedCount());
    assertEquals(3, dto.getReassignedMatchedCount());
    assertEquals(2, dto.getReassignedStillPendingCount());
    org.mockito.Mockito.verify(importOrderPersistenceService).reassignAllPendingOrders();
  }

  @Test
  void ensureManualMerchant_shouldCreateHiddenConfig() {
    when(merchantConfigRepository.existsByName("测试")).thenReturn(false);
    when(merchantConfigRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            inv -> {
              MerchantConfig entity = inv.getArgument(0);
              entity.setId(99L);
              return entity;
            });

    service.ensureManualMerchant("测试");

    org.mockito.ArgumentCaptor<MerchantConfig> captor =
        org.mockito.ArgumentCaptor.forClass(MerchantConfig.class);
    org.mockito.Mockito.verify(merchantConfigRepository).save(captor.capture());
    MerchantConfig saved = captor.getValue();
    assertEquals("测试", saved.getName());
    assertEquals(MerchantConfigVisibility.HIDDEN, saved.getVisibility());
    assertEquals("[]", saved.getKeywordsJson());
  }

  @Test
  void create_shouldPromoteHiddenMerchantToVisible() {
    MerchantConfig hidden = new MerchantConfig();
    hidden.setId(5L);
    hidden.setName("手工商家");
    hidden.setKeywordsJson("[]");
    hidden.setVisibility(MerchantConfigVisibility.HIDDEN);

    when(merchantConfigRepository.findByName("手工商家")).thenReturn(Optional.of(hidden));
    when(merchantConfigRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> inv.getArgument(0));
    when(importOrderPersistenceService.reassignAllPendingOrders())
        .thenReturn(new ReassignPendingOrdersResult(0, 0, 0));

    SaveMerchantConfigRequest request = new SaveMerchantConfigRequest();
    request.setName("手工商家");
    request.setKeywords(List.of("关键字"));

    var dto = service.create(request);
    assertEquals("手工商家", dto.getName());
    assertEquals(List.of("关键字"), dto.getKeywords());
    assertEquals(MerchantConfigVisibility.VISIBLE, hidden.getVisibility());
    verify(merchantConfigRepository).save(hidden);
    org.mockito.Mockito.verify(importOrderPersistenceService).reassignAllPendingOrders();
  }

  @Test
  void create_shouldRejectDuplicateVisibleMerchant() {
    MerchantConfig visible = new MerchantConfig();
    visible.setId(1L);
    visible.setName("商家A");
    visible.setKeywordsJson("[\"耐克\"]");
    visible.setVisibility(MerchantConfigVisibility.VISIBLE);

    when(merchantConfigRepository.findByName("商家A")).thenReturn(Optional.of(visible));

    SaveMerchantConfigRequest request = new SaveMerchantConfigRequest();
    request.setName("商家A");
    request.setKeywords(List.of("阿迪"));

    assertThrows(BusinessException.class, () -> service.create(request));
  }

  @Test
  void ensureManualMerchant_shouldSkipPendingName() {
    service.ensureManualMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
    org.mockito.Mockito.verify(merchantConfigRepository, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
  }
}
