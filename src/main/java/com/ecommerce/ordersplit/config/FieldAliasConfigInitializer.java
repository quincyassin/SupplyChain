package com.ecommerce.ordersplit.config;

import com.ecommerce.ordersplit.service.FieldAliasConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化字段别名默认配置
 *
 * @author huangxinsong
 */
@Component
@RequiredArgsConstructor
public class FieldAliasConfigInitializer implements ApplicationRunner {

    private final FieldAliasConfigService fieldAliasConfigService;

    @Override
    public void run(ApplicationArguments args) {
        fieldAliasConfigService.ensureDefaults();
    }
}
