package com.ecommerce.ordersplit.service;

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
import static org.mockito.Mockito.when;

/**
 * 商家配置服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class MerchantConfigServiceTest {

  @Mock private MerchantConfigRepository merchantConfigRepository;

  private MerchantConfigService service;

  @BeforeEach
  void setUp() {
    service = new MerchantConfigService(merchantConfigRepository, new ObjectMapper());
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
    when(merchantConfigRepository.existsByName("商家A")).thenReturn(false);
    when(merchantConfigRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            inv -> {
              MerchantConfig entity = inv.getArgument(0);
              entity.setId(10L);
              return entity;
            });

    SaveMerchantConfigRequest request = new SaveMerchantConfigRequest();
    request.setName("商家A");
    request.setKeywords(List.of("耐克", "AJ"));

    var dto = service.create(request);
    assertEquals("商家A", dto.getName());
    assertEquals(2, dto.getKeywords().size());
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
  void ensureConfigured_shouldRequireAnyMerchant() {
    when(merchantConfigRepository.count()).thenReturn(0L);
    org.junit.jupiter.api.Assertions.assertThrows(
        com.ecommerce.ordersplit.exception.BusinessException.class,
        () -> service.ensureConfigured());
  }

  @Test
  void ensureManualMerchant_shouldSkipPendingName() {
    service.ensureManualMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
    org.mockito.Mockito.verify(merchantConfigRepository, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
  }
}
