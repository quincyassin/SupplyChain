package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ColumnMappingItemDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.dto.OrderFieldDto;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.model.OrderFieldKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 列映射配置服务
 *
 * @author huangxinsong
 */
@Service
public class ColumnMappingService {

    /** 导入映射推荐字段（含当日表格所需列） */
    /** 平台表头映射可用字段（商家由关键字配置分单，不在 Excel 列映射中） */
    private static final OrderFieldKey[] STANDARD_FIELDS = {
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

    private final ObjectMapper objectMapper;
    private final FieldAliasConfigService fieldAliasConfigService;

    public ColumnMappingService(
            ObjectMapper objectMapper, FieldAliasConfigService fieldAliasConfigService) {
        this.objectMapper = objectMapper;
        this.fieldAliasConfigService = fieldAliasConfigService;
    }

    /** 默认输出映射：固定字段、固定顺序 */
    public ColumnMappingConfig defaultMapping() {
        return buildStandardMapping(null);
    }

    public List<OrderFieldDto> listFields() {
        List<OrderFieldDto> fields = new ArrayList<>();
        for (OrderFieldKey key : STANDARD_FIELDS) {
            fields.add(
                    new OrderFieldDto(
                            key.getCode(),
                            key.getLabel(),
                            key.isRequired(),
                            fieldAliasConfigService.getAliases(key)));
        }
        return fields;
    }

    /**
     * 根据 Excel 表头名称智能推荐映射（列序连续时使用）
     */
    public ColumnMappingConfig suggestMapping(List<String> excelHeaderNames) {
        if (excelHeaderNames == null) {
            return buildStandardMapping(null);
        }
        return buildStandardMapping(toSequentialHeaderDtos(excelHeaderNames));
    }

    /**
     * 根据 Excel 表头（含真实 columnIndex）智能推荐映射
     */
    public ColumnMappingConfig suggestMappingFromHeaders(List<ExcelHeaderDto> excelHeaders) {
        return buildStandardMapping(excelHeaders);
    }

    private ColumnMappingConfig buildStandardMapping(List<ExcelHeaderDto> excelHeaders) {
        ColumnMappingConfig config = new ColumnMappingConfig();
        Set<Integer> usedColumns = new LinkedHashSet<>();
        int sort = 0;
        for (OrderFieldKey fieldKey : STANDARD_FIELDS) {
            Integer matchedIndex =
                    excelHeaders == null
                            ? null
                            : findColumnIndex(excelHeaders, fieldKey, usedColumns);
            ColumnMappingItem item = new ColumnMappingItem();
            item.setFieldKey(fieldKey);
            if (excelHeaders == null) {
                // 无 Excel 表头上下文时仅作默认字段列表（导出等场景不依赖 sourceIndex）
                item.setSourceIndex(sort);
                item.setEnabled(true);
            } else if (matchedIndex != null) {
                item.setSourceIndex(matchedIndex);
                item.setEnabled(true);
                usedColumns.add(matchedIndex);
            } else {
                item.setSourceIndex(-1);
                item.setEnabled(false);
            }
            item.setSortOrder(sort++);
            config.getItems().add(item);
        }
        return config;
    }

    private List<ExcelHeaderDto> toSequentialHeaderDtos(List<String> headerNames) {
        List<ExcelHeaderDto> headers = new ArrayList<>();
        for (int i = 0; i < headerNames.size(); i++) {
            headers.add(new ExcelHeaderDto(i, headerNames.get(i)));
        }
        return headers;
    }

    public ColumnMappingConfig parseMappingJson(String mappingJson) {
        return parseMappingJson(mappingJson, true);
    }

    /**
     * 解析前端提交的列映射 JSON
     *
     * @param requireMerchant 是否要求映射商家列（发单分单场景应为 false）
     */
    public ColumnMappingConfig parseMappingJson(String mappingJson, boolean requireMerchant) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return null;
        }
        List<ColumnMappingItemDto> dtos;
        try {
            dtos = objectMapper.readValue(mappingJson, new TypeReference<List<ColumnMappingItemDto>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException(buildJsonParseErrorMessage(mappingJson, ex));
        }
        if (dtos == null || dtos.isEmpty()) {
            throw new BusinessException(
                    "列映射配置不能为空。请传 JSON 数组，例如：[{\"fieldKey\":\"orderNo\",\"sourceIndex\":0,\"enabled\":true,\"sortOrder\":0}]");
        }
        List<ColumnMappingItemDto> normalized = normalizeMappingDtos(dtos);
        if (normalized.isEmpty()) {
            throw new BusinessException("列映射配置中没有有效字段，请至少启用一个系统字段（已自动忽略 merchant 商家列）");
        }
        try {
            return fromDtos(normalized, requireMerchant);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("列映射字段不合法：" + ex.getMessage());
        }
    }

    private List<ColumnMappingItemDto> normalizeMappingDtos(List<ColumnMappingItemDto> dtos) {
        return dtos.stream()
                .filter(dto -> dto.getFieldKey() != null && !dto.getFieldKey().isBlank())
                .filter(dto -> !"merchant".equals(dto.getFieldKey().trim()))
                .collect(Collectors.toList());
    }

    private String buildJsonParseErrorMessage(String mappingJson, JsonProcessingException ex) {
        String preview = mappingJson.length() > 120 ? mappingJson.substring(0, 120) + "..." : mappingJson;
        String detail = ex.getOriginalMessage() == null ? ex.getMessage() : ex.getOriginalMessage();
        return "列映射 JSON 解析失败："
                + detail
                + "。请确认 multipart 字段 mapping 为 JSON 数组字符串。内容片段："
                + preview;
    }

    public ColumnMappingConfig fromDtos(List<ColumnMappingItemDto> dtos) {
        return fromDtos(dtos, true);
    }

    public ColumnMappingConfig fromDtos(List<ColumnMappingItemDto> dtos, boolean requireMerchant) {
        ColumnMappingConfig config = new ColumnMappingConfig();
        if (dtos == null) {
            return config;
        }
        Set<String> usedKeys = new HashSet<>();
        for (ColumnMappingItemDto dto : dtos) {
            if (dto.getFieldKey() == null || dto.getFieldKey().isBlank()) {
                continue;
            }
            if (!usedKeys.add(dto.getFieldKey())) {
                throw new BusinessException("字段「" + dto.getFieldKey() + "」重复配置");
            }
            ColumnMappingItem item = new ColumnMappingItem();
            item.setFieldKey(OrderFieldKey.fromCode(dto.getFieldKey()));
            item.setSourceIndex(dto.getSourceIndex() == null ? -1 : dto.getSourceIndex());
            item.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            item.setSortOrder(dto.getSortOrder() == null ? config.getItems().size() : dto.getSortOrder());
            config.getItems().add(item);
        }
        validate(config, requireMerchant);
        return config;
    }

    public List<ColumnMappingItemDto> toDtos(ColumnMappingConfig config) {
        List<ColumnMappingItemDto> dtos = new ArrayList<>();
        for (ColumnMappingItem item : config.getItems()) {
            ColumnMappingItemDto dto = new ColumnMappingItemDto();
            dto.setFieldKey(item.getFieldKey().getCode());
            dto.setSourceIndex(item.getSourceIndex());
            dto.setEnabled(item.isEnabled());
            dto.setSortOrder(item.getSortOrder());
            dtos.add(dto);
        }
        return dtos;
    }

    /** 平台模板表头解析结果（表头 + 同步后的列映射） */
    public record PlatformTemplateHeadersResolveResult(
            List<ExcelHeaderDto> templateHeaders, List<ColumnMappingItemDto> mapping) {}

    /**
     * 平台模板保存/加载：移除未被映射引用的自动物流列，未绑定物流字段时再补全标准列
     */
    public PlatformTemplateHeadersResolveResult resolvePlatformTemplateHeaders(
            List<ExcelHeaderDto> templateHeaders, List<ColumnMappingItemDto> mapping) {
        PlatformTemplateHeadersResolveResult cleaned =
                removeOrphanAutoLogisticsColumns(templateHeaders, mapping);
        List<ExcelHeaderDto> enriched =
                ensureLogisticsTemplateHeaders(cleaned.templateHeaders(), cleaned.mapping());
        return new PlatformTemplateHeadersResolveResult(enriched, cleaned.mapping());
    }

    /**
     * 平台模板表头补全：未绑定物流字段时，在最前面按「物流公司、物流单号」顺序插入标准列
     */
    public List<ExcelHeaderDto> ensureLogisticsTemplateHeaders(List<ExcelHeaderDto> templateHeaders) {
        return ensureLogisticsTemplateHeaders(templateHeaders, null);
    }

    public List<ExcelHeaderDto> ensureLogisticsTemplateHeaders(
            List<ExcelHeaderDto> templateHeaders, List<ColumnMappingItemDto> mapping) {
        if (templateHeaders == null || templateHeaders.isEmpty()) {
            return List.of();
        }
        boolean needCompany = !isLogisticsFieldMapped(mapping, OrderFieldKey.LOGISTICS_COMPANY);
        boolean needNo = !isLogisticsFieldMapped(mapping, OrderFieldKey.LOGISTICS_NO);
        if (!needCompany && !needNo) {
            return new ArrayList<>(templateHeaders);
        }

        boolean hasCompany =
                !needCompany
                        || hasTemplateHeaderForField(
                                templateHeaders, OrderFieldKey.LOGISTICS_COMPANY);
        boolean hasNo =
                !needNo
                        || hasTemplateHeaderForField(templateHeaders, OrderFieldKey.LOGISTICS_NO);
        if (hasCompany && hasNo) {
            return new ArrayList<>(templateHeaders);
        }

        List<ExcelHeaderDto> toPrepend = new ArrayList<>();
        if (needCompany
                && !hasTemplateHeaderForField(templateHeaders, OrderFieldKey.LOGISTICS_COMPANY)) {
            toPrepend.add(new ExcelHeaderDto(0, OrderFieldKey.LOGISTICS_COMPANY.getLabel()));
        }
        if (needNo && !hasTemplateHeaderForField(templateHeaders, OrderFieldKey.LOGISTICS_NO)) {
            toPrepend.add(new ExcelHeaderDto(0, OrderFieldKey.LOGISTICS_NO.getLabel()));
        }
        int prependCount = toPrepend.size();
        for (int i = 0; i < toPrepend.size(); i++) {
            toPrepend.get(i).setColumnIndex(i);
        }

        List<ExcelHeaderDto> result = new ArrayList<>(toPrepend);
        for (ExcelHeaderDto existing : templateHeaders) {
            result.add(
                    new ExcelHeaderDto(
                            existing.getColumnIndex() + prependCount, existing.getHeaderName()));
        }
        return result;
    }

    /**
     * 删除系统自动补的标准物流列（表头名精确为「物流单号」「物流公司」），且当前映射未引用该列
     */
    PlatformTemplateHeadersResolveResult removeOrphanAutoLogisticsColumns(
            List<ExcelHeaderDto> templateHeaders, List<ColumnMappingItemDto> mapping) {
        if (templateHeaders == null || templateHeaders.isEmpty()) {
            return new PlatformTemplateHeadersResolveResult(List.of(), mapping);
        }
        List<ColumnMappingItemDto> safeMapping = mapping == null ? List.of() : mapping;
        Integer logisticsNoIndex = findMappedColumnIndex(safeMapping, OrderFieldKey.LOGISTICS_NO);
        Integer logisticsCompanyIndex =
                findMappedColumnIndex(safeMapping, OrderFieldKey.LOGISTICS_COMPANY);

        List<ExcelHeaderDto> sorted = new ArrayList<>(templateHeaders);
        sorted.sort(Comparator.comparingInt(ExcelHeaderDto::getColumnIndex));

        List<ExcelHeaderDto> kept = new ArrayList<>();
        Set<Integer> removedIndices = new HashSet<>();
        for (ExcelHeaderDto header : sorted) {
            int columnIndex = header.getColumnIndex();
            String headerName = header.getHeaderName();
            if (isAutoGeneratedLogisticsLabel(headerName, OrderFieldKey.LOGISTICS_NO)
                    && !Integer.valueOf(columnIndex).equals(logisticsNoIndex)) {
                removedIndices.add(columnIndex);
                continue;
            }
            if (isAutoGeneratedLogisticsLabel(headerName, OrderFieldKey.LOGISTICS_COMPANY)
                    && !Integer.valueOf(columnIndex).equals(logisticsCompanyIndex)) {
                removedIndices.add(columnIndex);
                continue;
            }
            kept.add(header);
        }

        List<ExcelHeaderDto> reindexed = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            reindexed.add(new ExcelHeaderDto(i, kept.get(i).getHeaderName()));
        }
        List<ColumnMappingItemDto> adjustedMapping =
                adjustMappingAfterColumnRemoval(safeMapping, removedIndices);
        return new PlatformTemplateHeadersResolveResult(reindexed, adjustedMapping);
    }

    /**
     * 订单导入匹配平台模板时：模板中自动补的物流列若上传 Excel 没有，则不参与 100% 表头校验
     */
    public List<String> templateHeaderNamesForImportMatch(
            List<ExcelHeaderDto> templateHeaders, List<String> uploadHeaderNames) {
        if (templateHeaders == null || templateHeaders.isEmpty()) {
            return List.of();
        }
        List<String> normalizedUpload =
                uploadHeaderNames == null ? List.of() : uploadHeaderNames;
        boolean uploadHasLogisticsNo =
                uploadHasLogisticsField(normalizedUpload, OrderFieldKey.LOGISTICS_NO);
        boolean uploadHasLogisticsCompany =
                uploadHasLogisticsField(normalizedUpload, OrderFieldKey.LOGISTICS_COMPANY);
        List<String> result = new ArrayList<>();
        for (ExcelHeaderDto header : templateHeaders) {
            String name = header.getHeaderName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (fieldAliasConfigService.headerMatchesField(name, OrderFieldKey.LOGISTICS_NO)
                    && !uploadHasLogisticsNo) {
                continue;
            }
            if (fieldAliasConfigService.headerMatchesField(name, OrderFieldKey.LOGISTICS_COMPANY)
                    && !uploadHasLogisticsCompany) {
                continue;
            }
            result.add(name);
        }
        return result;
    }

    /**
     * 将已保存映射与标准系统字段合并，确保物流等新增字段默认出现在平台模板中
     */
    public List<ColumnMappingItemDto> mergePlatformMapping(
            List<ColumnMappingItemDto> saved, List<ExcelHeaderDto> templateHeaders) {
        Map<String, ColumnMappingItemDto> savedByKey = new LinkedHashMap<>();
        if (saved != null) {
            for (ColumnMappingItemDto dto : saved) {
                if (dto.getFieldKey() != null && !dto.getFieldKey().isBlank()) {
                    savedByKey.put(dto.getFieldKey().trim(), dto);
                }
            }
        }
        ColumnMappingConfig suggested = buildStandardMapping(templateHeaders);

        List<ColumnMappingItemDto> result = new ArrayList<>();
        int sort = 0;
        for (ColumnMappingItem suggestedItem : suggested.getItems()) {
            String code = suggestedItem.getFieldKey().getCode();
            ColumnMappingItemDto savedDto = savedByKey.get(code);
            ColumnMappingItemDto merged = new ColumnMappingItemDto();
            merged.setFieldKey(code);
            merged.setSortOrder(sort++);
            if (savedDto != null) {
                int savedIndex =
                        savedDto.getSourceIndex() == null ? -1 : savedDto.getSourceIndex();
                boolean savedEnabled = savedDto.getEnabled() == null || savedDto.getEnabled();
                if (!savedEnabled) {
                    merged.setSourceIndex(-1);
                    merged.setEnabled(false);
                } else if (savedIndex >= 0 && columnExistsInTemplate(templateHeaders, savedIndex)) {
                    // 用户已启用的有效列索引以保存配置为准，避免改绑后被表头自动匹配覆盖
                    merged.setSourceIndex(savedIndex);
                    merged.setEnabled(true);
                } else if (suggestedItem.isEnabled() && suggestedItem.getSourceIndex() >= 0) {
                    merged.setSourceIndex(suggestedItem.getSourceIndex());
                    merged.setEnabled(true);
                } else {
                    merged.setSourceIndex(-1);
                    merged.setEnabled(false);
                }
            } else {
                merged.setSourceIndex(suggestedItem.getSourceIndex());
                merged.setEnabled(suggestedItem.isEnabled());
            }
            result.add(merged);
        }
        return result;
    }

    public void validate(ColumnMappingConfig config) {
        validate(config, true);
    }

    public void validateForDailyTable(ColumnMappingConfig config) {
        validate(config, false);
    }

    public boolean hasMappedMerchant(ColumnMappingConfig config) {
        if (config == null || config.getItems().isEmpty()) {
            return false;
        }
        return config.getItems().stream()
                .anyMatch(
                        item -> item.isEnabled()
                                && item.getFieldKey() == OrderFieldKey.MERCHANT
                                && item.getSourceIndex() >= 0);
    }

    private void validate(ColumnMappingConfig config, boolean requireMerchant) {
        if (config == null || config.getItems().isEmpty()) {
            throw new BusinessException("请配置至少一个列映射");
        }
        if (requireMerchant) {
            boolean hasMerchant = config.getItems().stream()
                    .anyMatch(
                            item -> item.isEnabled()
                                    && item.getFieldKey() == OrderFieldKey.MERCHANT
                                    && item.getSourceIndex() >= 0);
            if (!hasMerchant) {
                throw new BusinessException("必须启用并映射「商家」字段");
            }
        }
        for (OrderFieldKey fieldKey : OrderFieldKey.values()) {
            if (!fieldKey.isRequired()) {
                continue;
            }
            if (fieldKey == OrderFieldKey.MERCHANT) {
                continue;
            }
            boolean mapped = config.getItems().stream()
                    .anyMatch(
                            item -> item.isEnabled()
                                    && item.getFieldKey() == fieldKey
                                    && item.getSourceIndex() >= 0);
            if (!mapped) {
                throw new BusinessException("必须启用并映射「" + fieldKey.getLabel() + "」字段");
            }
        }
        for (ColumnMappingItem item : config.getItems()) {
            if (!item.isEnabled()) {
                continue;
            }
            if (item.getSourceIndex() < 0) {
                throw new BusinessException("字段「" + item.getFieldKey().getLabel() + "」未选择 Excel 列");
            }
        }
    }

    private Integer findColumnIndex(
            List<ExcelHeaderDto> headers, OrderFieldKey fieldKey, Set<Integer> usedColumns) {
        for (ExcelHeaderDto headerDto : headers) {
            int columnIndex = headerDto.getColumnIndex();
            if (usedColumns.contains(columnIndex)) {
                continue;
            }
            String header = headerDto.getHeaderName() == null ? "" : headerDto.getHeaderName().trim();
            if (header.isEmpty()) {
                continue;
            }
            if (matchesField(header, fieldKey)) {
                return columnIndex;
            }
        }
        return null;
    }

    private boolean matchesField(String header, OrderFieldKey fieldKey) {
        return fieldAliasConfigService.headerMatchesField(header, fieldKey);
    }

    private boolean columnIndexMatchesField(
            List<ExcelHeaderDto> headers, int columnIndex, OrderFieldKey fieldKey) {
        for (ExcelHeaderDto header : headers) {
            if (header.getColumnIndex() == columnIndex) {
                return headerMatchesField(header.getHeaderName(), fieldKey);
            }
        }
        return false;
    }

    private boolean columnExistsInTemplate(List<ExcelHeaderDto> headers, int columnIndex) {
        for (ExcelHeaderDto header : headers) {
            if (header.getColumnIndex() == columnIndex) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTemplateHeaderForField(
            List<ExcelHeaderDto> headers, OrderFieldKey fieldKey) {
        for (ExcelHeaderDto header : headers) {
            if (fieldAliasConfigService.headerMatchesField(header.getHeaderName(), fieldKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean uploadHasLogisticsField(
            List<String> uploadHeaderNames, OrderFieldKey fieldKey) {
        for (String headerName : uploadHeaderNames) {
            if (fieldAliasConfigService.headerMatchesField(headerName, fieldKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean headerMatchesField(String headerName, OrderFieldKey fieldKey) {
        return fieldAliasConfigService.headerMatchesField(headerName, fieldKey);
    }

    private boolean isLogisticsFieldMapped(
            List<ColumnMappingItemDto> mapping, OrderFieldKey fieldKey) {
        Integer mappedIndex = findMappedColumnIndex(mapping, fieldKey);
        return mappedIndex != null;
    }

    private Integer findMappedColumnIndex(
            List<ColumnMappingItemDto> mapping, OrderFieldKey fieldKey) {
        if (mapping == null) {
            return null;
        }
        String code = fieldKey.getCode();
        for (ColumnMappingItemDto dto : mapping) {
            if (!code.equals(dto.getFieldKey())) {
                continue;
            }
            boolean enabled = dto.getEnabled() == null || dto.getEnabled();
            int sourceIndex = dto.getSourceIndex() == null ? -1 : dto.getSourceIndex();
            if (enabled && sourceIndex >= 0) {
                return sourceIndex;
            }
            return null;
        }
        return null;
    }

    private boolean isAutoGeneratedLogisticsLabel(String headerName, OrderFieldKey fieldKey) {
        if (headerName == null) {
            return false;
        }
        return headerName.trim().equals(fieldKey.getLabel());
    }

    private List<ColumnMappingItemDto> adjustMappingAfterColumnRemoval(
            List<ColumnMappingItemDto> mapping, Set<Integer> removedIndices) {
        if (mapping == null || mapping.isEmpty()) {
            return mapping == null ? List.of() : mapping;
        }
        if (removedIndices == null || removedIndices.isEmpty()) {
            return mapping;
        }
        List<Integer> removedSorted =
                removedIndices.stream().sorted().toList();
        List<ColumnMappingItemDto> adjusted = new ArrayList<>();
        for (ColumnMappingItemDto dto : mapping) {
            ColumnMappingItemDto copy = new ColumnMappingItemDto();
            copy.setFieldKey(dto.getFieldKey());
            copy.setEnabled(dto.getEnabled());
            copy.setSortOrder(dto.getSortOrder());
            int sourceIndex = dto.getSourceIndex() == null ? -1 : dto.getSourceIndex();
            if (sourceIndex >= 0) {
                if (removedIndices.contains(sourceIndex)) {
                    copy.setSourceIndex(-1);
                    copy.setEnabled(false);
                } else {
                    int shift = 0;
                    for (int removedIndex : removedSorted) {
                        if (removedIndex < sourceIndex) {
                            shift++;
                        }
                    }
                    copy.setSourceIndex(sourceIndex - shift);
                }
            } else {
                copy.setSourceIndex(-1);
            }
            adjusted.add(copy);
        }
        return adjusted;
    }
}
