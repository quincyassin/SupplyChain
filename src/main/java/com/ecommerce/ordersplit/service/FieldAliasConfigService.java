package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.FieldAliasConfigDto;
import com.ecommerce.ordersplit.dto.SaveFieldAliasConfigRequest;
import com.ecommerce.ordersplit.entity.FieldAliasConfig;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.ecommerce.ordersplit.repository.FieldAliasConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统字段别名配置（Excel 表头智能匹配）
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class FieldAliasConfigService {

    private static final int ALIAS_MAX_LENGTH = 128;
    private static final int ALIAS_MAX_COUNT = 50;

    /** 与 ColumnMappingService 导入映射字段一致 */
    private static final OrderFieldKey[] MAPPING_FIELDS = {
            OrderFieldKey.ORDER_NO,
            OrderFieldKey.PRODUCT_NAME,
            OrderFieldKey.LOGISTICS_NO,
            OrderFieldKey.LOGISTICS_COMPANY,
            OrderFieldKey.RECEIVER,
            OrderFieldKey.PHONE,
            OrderFieldKey.ADDRESS,
            OrderFieldKey.SKU,
            OrderFieldKey.QUANTITY,
            OrderFieldKey.SHIPPING_FEE,
            OrderFieldKey.REMARK,
            OrderFieldKey.AFTER_SALES_REMARK
    };

    private final FieldAliasConfigRepository fieldAliasConfigRepository;
    private final ObjectMapper objectMapper;

    private volatile Map<String, List<String>> aliasCache = Map.of();

    @PostConstruct
    void warmCache() {
        reloadCache();
    }

    @Transactional
    public void ensureDefaults() {
        if (fieldAliasConfigRepository.count() > 0) {
            reloadCache();
            return;
        }
        for (OrderFieldKey fieldKey : MAPPING_FIELDS) {
            FieldAliasConfig entity = new FieldAliasConfig();
            entity.setFieldKey(fieldKey.getCode());
            entity.setAliasesJson(writeAliases(defaultAliases(fieldKey)));
            fieldAliasConfigRepository.save(entity);
        }
        reloadCache();
    }

    @Transactional(readOnly = true)
    public List<FieldAliasConfigDto> listAll() {
        Map<String, FieldAliasConfig> savedByKey = new LinkedHashMap<>();
        for (FieldAliasConfig entity : fieldAliasConfigRepository.findAllByOrderByFieldKeyAsc()) {
            savedByKey.put(entity.getFieldKey(), entity);
        }
        List<FieldAliasConfigDto> result = new ArrayList<>();
        for (OrderFieldKey fieldKey : MAPPING_FIELDS) {
            FieldAliasConfig entity = savedByKey.get(fieldKey.getCode());
            result.add(toDto(fieldKey, entity));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<String> getAliases(OrderFieldKey fieldKey) {
        List<String> aliases = aliasCache.get(fieldKey.getCode());
        if (aliases == null || aliases.isEmpty()) {
            return List.copyOf(defaultAliases(fieldKey));
        }
        return aliases;
    }

    @Transactional
    public FieldAliasConfigDto save(String fieldKeyCode, SaveFieldAliasConfigRequest request) {
        OrderFieldKey fieldKey = resolveMappingField(fieldKeyCode);
        List<String> aliases = normalizeAliases(request == null ? null : request.getAliases(), fieldKey);
        FieldAliasConfig entity = fieldAliasConfigRepository
                .findByFieldKey(fieldKey.getCode())
                .orElseGet(() -> {
                    FieldAliasConfig created = new FieldAliasConfig();
                    created.setFieldKey(fieldKey.getCode());
                    return created;
                });
        entity.setAliasesJson(writeAliases(aliases));
        fieldAliasConfigRepository.save(entity);
        reloadCache();
        return toDto(fieldKey, entity);
    }

    public boolean headerMatchesField(String header, OrderFieldKey fieldKey) {
        String normalizedHeader = normalizeToken(header);
        if (normalizedHeader.isEmpty()) {
            return false;
        }
        String label = normalizeToken(fieldKey.getLabel());
        if (matchesToken(normalizedHeader, label)) {
            return true;
        }
        for (String alias : getAliases(fieldKey)) {
            if (matchesToken(normalizedHeader, normalizeToken(alias))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesToken(String header, String token) {
        if (token.isEmpty()) {
            return false;
        }
        return header.equals(token);
    }

    private OrderFieldKey resolveMappingField(String fieldKeyCode) {
        if (fieldKeyCode == null || fieldKeyCode.isBlank()) {
            throw new BusinessException("字段 key 无效");
        }
        OrderFieldKey fieldKey = OrderFieldKey.fromCode(fieldKeyCode.trim());
        for (OrderFieldKey allowed : MAPPING_FIELDS) {
            if (allowed == fieldKey) {
                return fieldKey;
            }
        }
        throw new BusinessException("字段「" + fieldKey.getLabel() + "」不支持别名配置");
    }

    private List<String> normalizeAliases(List<String> aliases, OrderFieldKey fieldKey) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        String label = normalizeToken(fieldKey.getLabel());
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String trimmed = alias.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > ALIAS_MAX_LENGTH) {
                throw new BusinessException(
                        "别名「" + trimmed + "」不能超过 " + ALIAS_MAX_LENGTH + " 个字符");
            }
            String token = normalizeToken(trimmed);
            if (token.equals(label)) {
                continue;
            }
            normalized.add(trimmed);
            if (normalized.size() > ALIAS_MAX_COUNT) {
                throw new BusinessException("每个字段最多配置 " + ALIAS_MAX_COUNT + " 个别名");
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> defaultAliases(OrderFieldKey fieldKey) {
        return switch (fieldKey) {
            case ORDER_NO -> List.of("订单号");
            case PRODUCT_NAME -> List.of("名称", "商品名");
            case SKU -> List.of("SKU", "规格", "规格型号");
            case RECEIVER -> List.of("姓名", "收货人姓名", "收件人");
            case PHONE -> List.of("联系电话", "手机", "手机号", "电话");
            case ADDRESS -> List.of("收货地址", "地址");
            case SHIPPING_FEE -> List.of("邮费", "快递费");
            case REMARK -> List.of("买家留言", "卖家备注", "订单备注");
            case AFTER_SALES_REMARK -> List.of("售后备注", "退货原因", "退款原因");
            case LOGISTICS_NO -> List.of("快递单号", "运单号");
            case LOGISTICS_COMPANY -> List.of("快递公司", "承运商");
            default -> List.of();
        };
    }

    private FieldAliasConfigDto toDto(OrderFieldKey fieldKey, FieldAliasConfig entity) {
        FieldAliasConfigDto dto = new FieldAliasConfigDto();
        dto.setFieldKey(fieldKey.getCode());
        dto.setLabel(fieldKey.getLabel());
        dto.setAliases(entity == null ? defaultAliases(fieldKey) : readAliases(entity));
        dto.setUpdatedAt(entity == null ? null : entity.getUpdatedAt());
        return dto;
    }

    private List<String> readAliases(FieldAliasConfig entity) {
        if (entity.getAliasesJson() == null || entity.getAliasesJson().isBlank()) {
            return List.of();
        }
        try {
            List<String> aliases =
                    objectMapper.readValue(entity.getAliasesJson(), new TypeReference<List<String>>() {});
            return aliases == null ? List.of() : aliases;
        } catch (Exception ex) {
            throw new BusinessException("字段别名配置解析失败：" + fieldKeyLabel(entity.getFieldKey()));
        }
    }

    private String fieldKeyLabel(String fieldKeyCode) {
        try {
            return OrderFieldKey.fromCode(fieldKeyCode).getLabel();
        } catch (IllegalArgumentException ex) {
            return fieldKeyCode;
        }
    }

    private String writeAliases(List<String> aliases) {
        try {
            return objectMapper.writeValueAsString(aliases == null ? List.of() : aliases);
        } catch (Exception ex) {
            throw new BusinessException("字段别名配置保存失败");
        }
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void reloadCache() {
        Map<String, List<String>> next = new LinkedHashMap<>();
        for (FieldAliasConfig entity : fieldAliasConfigRepository.findAllByOrderByFieldKeyAsc()) {
            next.put(entity.getFieldKey(), List.copyOf(readAliases(entity)));
        }
        aliasCache = Map.copyOf(next);
    }
}
