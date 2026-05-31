package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.repository.FieldAliasConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mockito.Mockito;

/**
 * 列映射测试辅助
 *
 * @author huangxinsong
 */
final class ColumnMappingTestFixtures {

    private ColumnMappingTestFixtures() {}

    static ColumnMappingService createColumnMappingService() {
        FieldAliasConfigRepository repository = Mockito.mock(FieldAliasConfigRepository.class);
        Mockito.when(repository.count()).thenReturn(1L);
        Mockito.when(repository.findAllByOrderByFieldKeyAsc()).thenReturn(List.of());
        FieldAliasConfigService fieldAliasConfigService =
                new FieldAliasConfigService(repository, new ObjectMapper());
        fieldAliasConfigService.ensureDefaults();
        return new ColumnMappingService(new ObjectMapper(), fieldAliasConfigService);
    }
}
