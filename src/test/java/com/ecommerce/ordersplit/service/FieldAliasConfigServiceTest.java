package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.SaveFieldAliasConfigRequest;
import com.ecommerce.ordersplit.entity.FieldAliasConfig;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.repository.FieldAliasConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字段别名配置服务测试
 *
 * @author huangxinsong
 */
@ExtendWith(MockitoExtension.class)
class FieldAliasConfigServiceTest {

    @Mock
    private FieldAliasConfigRepository fieldAliasConfigRepository;

    private FieldAliasConfigService fieldAliasConfigService;

    @BeforeEach
    void setUp() {
        fieldAliasConfigService =
                new FieldAliasConfigService(fieldAliasConfigRepository, new ObjectMapper());
    }

    @Test
    void ensureDefaults_shouldSeedAllMappingFieldsWhenEmpty() {
        when(fieldAliasConfigRepository.count()).thenReturn(0L);
        when(fieldAliasConfigRepository.save(any(FieldAliasConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fieldAliasConfigService.ensureDefaults();

        verify(fieldAliasConfigRepository, times(12)).save(any(FieldAliasConfig.class));
    }

    @Test
    void headerMatchesField_shouldNotPartialMatch() {
        when(fieldAliasConfigRepository.count()).thenReturn(1L);
        when(fieldAliasConfigRepository.findAllByOrderByFieldKeyAsc())
                .thenReturn(List.of());

        fieldAliasConfigService.ensureDefaults();

        assertFalse(fieldAliasConfigService.headerMatchesField("收货人地址", OrderFieldKey.RECEIVER));
        assertFalse(fieldAliasConfigService.headerMatchesField("收货人电话", OrderFieldKey.RECEIVER));
        assertFalse(fieldAliasConfigService.headerMatchesField("收货人", OrderFieldKey.PHONE));
        assertFalse(fieldAliasConfigService.headerMatchesField("收货人", OrderFieldKey.ADDRESS));
        assertFalse(fieldAliasConfigService.headerMatchesField("订单编号备注", OrderFieldKey.ORDER_NO));
        assertTrue(fieldAliasConfigService.headerMatchesField("订单编号", OrderFieldKey.ORDER_NO));
        assertTrue(fieldAliasConfigService.headerMatchesField("收货人", OrderFieldKey.RECEIVER));
        assertTrue(fieldAliasConfigService.headerMatchesField("收货人电话", OrderFieldKey.PHONE));
    }

    @Test
    void headerMatchesField_shouldMatchConfiguredReceiverAlias() {
        when(fieldAliasConfigRepository.count()).thenReturn(1L);
        when(fieldAliasConfigRepository.findAllByOrderByFieldKeyAsc())
                .thenReturn(
                        List.of(
                                entity(
                                        "receiver",
                                        "[\"姓名\",\"收件人\",\"收货人姓名\"]")));

        fieldAliasConfigService.ensureDefaults();

        assertTrue(fieldAliasConfigService.headerMatchesField("收件人", OrderFieldKey.RECEIVER));
        assertTrue(fieldAliasConfigService.headerMatchesField("姓名", OrderFieldKey.RECEIVER));
    }

    @Test
    void save_shouldIgnoreSystemLabelAlias() {
        when(fieldAliasConfigRepository.findByFieldKey("receiver"))
                .thenReturn(Optional.of(entity("receiver", "[]")));
        when(fieldAliasConfigRepository.save(any(FieldAliasConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldAliasConfigRepository.findAllByOrderByFieldKeyAsc())
                .thenReturn(List.of(entity("receiver", "[\"收件人\"]")));

        SaveFieldAliasConfigRequest request = new SaveFieldAliasConfigRequest();
        request.setAliases(List.of("收货人", "收件人"));

        var dto = fieldAliasConfigService.save("receiver", request);

        assertEquals(List.of("收件人"), dto.getAliases());
    }

    @Test
    void save_shouldRejectUnsupportedField() {
        SaveFieldAliasConfigRequest request = new SaveFieldAliasConfigRequest();
        request.setAliases(List.of("别名"));
        assertThrows(BusinessException.class, () -> fieldAliasConfigService.save("merchant", request));
        verify(fieldAliasConfigRepository, never()).save(any(FieldAliasConfig.class));
    }

    private FieldAliasConfig entity(String fieldKey, String aliasesJson) {
        FieldAliasConfig entity = new FieldAliasConfig();
        entity.setFieldKey(fieldKey);
        entity.setAliasesJson(aliasesJson);
        return entity;
    }
}
