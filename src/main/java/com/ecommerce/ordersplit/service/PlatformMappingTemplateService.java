package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.ColumnMappingItemDto;
import com.ecommerce.ordersplit.dto.ExcelHeaderDto;
import com.ecommerce.ordersplit.dto.PlatformExportTemplateDto;
import com.ecommerce.ordersplit.dto.PlatformTemplateDetailDto;
import com.ecommerce.ordersplit.dto.PlatformTemplateSummaryDto;
import com.ecommerce.ordersplit.dto.SavePlatformTemplateRequest;
import com.ecommerce.ordersplit.entity.PlatformMappingTemplate;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.ColumnMappingConfig;
import com.ecommerce.ordersplit.model.ColumnMappingItem;
import com.ecommerce.ordersplit.repository.PlatformMappingTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台表头模板服务
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class PlatformMappingTemplateService {

  /** 平台模板表头需全部出现在上传 Excel 中（100% 匹配） */
  private static final double MIN_TEMPLATE_MATCH_RATIO = 1.0;

  /** 最高分与次高分至少相差的列数，避免多个平台表头完全一致时误选 */
  private static final int MIN_SCORE_GAP = 1;

  private final PlatformMappingTemplateRepository templateRepository;
  private final ColumnMappingService columnMappingService;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<PlatformTemplateSummaryDto> listSummaries() {
    return templateRepository.findAllByOrderByUpdatedAtDesc().stream()
        .map(
            entity ->
                new PlatformTemplateSummaryDto(
                    entity.getPlatform(),
                    entity.getTemplateFileName(),
                    entity.getUpdatedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public PlatformTemplateDetailDto getDetail(String platform) {
    return toDetail(getRequired(platform));
  }

  /**
   * 加载平台导出模板（表头与列映射，用于回单导出）
   */
  @Transactional(readOnly = true)
  public PlatformExportTemplateDto resolveExportTemplate(String platform) {
    String normalized = normalizePlatform(platform);
    PlatformMappingTemplate entity = getRequired(normalized);
    List<ExcelHeaderDto> templateHeaders = loadTemplateHeaders(entity);
    if (templateHeaders.isEmpty()) {
      throw new BusinessException("平台「" + normalized + "」尚未上传模板 Excel，请先在系统配置中配置表头映射");
    }
    List<ColumnMappingItemDto> mappingDtos = readMappingDtos(entity);
    if (mappingDtos.isEmpty()) {
      throw new BusinessException("平台「" + normalized + "」尚未配置列映射");
    }
    List<ColumnMappingItemDto> mergedMapping =
        columnMappingService.mergePlatformMapping(mappingDtos, templateHeaders);
    ColumnMappingConfig mapping = columnMappingService.fromDtos(mergedMapping, false);
    return new PlatformExportTemplateDto(normalized, mapping, templateHeaders);
  }

  @Transactional(readOnly = true)
  public boolean exists(String platform) {
    return templateRepository.existsByPlatform(normalizePlatform(platform));
  }

  /**
   * 解析导入平台：唯一匹配直接给出结果，多平台同分时标记为歧义供前端手动选择
   */
  @Transactional(readOnly = true)
  public PlatformHeaderMatchResult resolveImportPlatform(List<ExcelHeaderDto> uploadHeaders) {
    List<TemplateHeaderMatch> qualified = findMatchingPlatforms(uploadHeaders);
    if (qualified.isEmpty()) {
      throw new BusinessException(
          "未找到与当前 Excel 表头完全一致的平台模板（需与某平台模板表头 100% 对应）。"
              + "若为新平台订单，请先在「系统配置 → 表头映射」中创建该平台并上传对应模板表头");
    }

    TemplateHeaderMatch best = qualified.get(0);
    if (qualified.size() >= 2) {
      int secondScore = qualified.get(1).matchScore();
      if (best.matchScore() - secondScore < MIN_SCORE_GAP) {
        List<TemplateHeaderMatch> topScoreMatches =
            qualified.stream()
                .filter(match -> match.matchScore() == best.matchScore())
                .toList();
        return PlatformHeaderMatchResult.ambiguous(topScoreMatches);
      }
    }
    return PlatformHeaderMatchResult.unique(best);
  }

  /**
   * 查找所有与上传表头 100% 匹配的平台模板（按得分降序）
   */
  @Transactional(readOnly = true)
  public List<TemplateHeaderMatch> findMatchingPlatforms(List<ExcelHeaderDto> uploadHeaders) {
    List<PlatformMappingTemplate> templates = templateRepository.findAllByOrderByUpdatedAtDesc();
    if (templates.isEmpty()) {
      throw new BusinessException("尚未配置任何平台模板，请先到「系统配置 → 表头映射」中添加");
    }

    List<String> normalizedUpload = normalizeHeaderNames(extractHeaderNames(uploadHeaders));
    if (normalizedUpload.isEmpty()) {
      throw new BusinessException("Excel 表头为空，无法匹配平台模板");
    }

    List<TemplateHeaderMatch> qualified = new ArrayList<>();
    for (PlatformMappingTemplate entity : templates) {
      List<ExcelHeaderDto> templateHeaders = loadTemplateHeaders(entity);
      List<String> templateHeaderNames =
          columnMappingService.templateHeaderNamesForImportMatch(
              templateHeaders, normalizedUpload);
      if (templateHeaderNames.isEmpty()) {
        continue;
      }
      int score = scoreHeaderMatch(normalizedUpload, templateHeaderNames);
      double ratio = (double) score / templateHeaderNames.size();
      if (ratio < MIN_TEMPLATE_MATCH_RATIO) {
        continue;
      }
      ColumnMappingConfig mapping =
          resolveMappingForHeaders(entity.getPlatform(), uploadHeaders);
      qualified.add(new TemplateHeaderMatch(entity.getPlatform(), mapping, score));
    }

    qualified.sort((left, right) -> Integer.compare(right.matchScore(), left.matchScore()));
    return qualified;
  }

  /**
   * 自动匹配唯一平台；若多个平台同分则抛错（无用户选择时的严格模式）
   */
  @Transactional(readOnly = true)
  public TemplateHeaderMatch matchByHeaders(List<ExcelHeaderDto> uploadHeaders) {
    PlatformHeaderMatchResult result = resolveImportPlatform(uploadHeaders);
    if (result.ambiguous()) {
      String candidates =
          result.candidates().stream()
              .limit(3)
              .map(TemplateHeaderMatch::platform)
              .collect(Collectors.joining("、"));
      throw new BusinessException(
          "表头同时接近多个平台模板（"
              + candidates
              + "），无法自动识别。请选择订单来源平台后再导入");
    }
    return result.selected();
  }

  /**
   * 按用户指定平台匹配：校验表头确实命中该平台模板
   */
  @Transactional(readOnly = true)
  public TemplateHeaderMatch matchByPlatform(
      String platform, List<ExcelHeaderDto> uploadHeaders) {
    String normalized = normalizePlatform(platform);
    List<TemplateHeaderMatch> matches = findMatchingPlatforms(uploadHeaders);
    for (TemplateHeaderMatch match : matches) {
      if (normalized.equals(match.platform())) {
        return match;
      }
    }
    if (!exists(normalized)) {
      throw new BusinessException(
          "平台「" + normalized + "」尚未配置表头模板，请先在系统配置中添加");
    }
    throw new BusinessException(
        "当前 Excel 表头与平台「" + normalized + "」的模板不一致，无法按该平台导入");
  }

  @Transactional(readOnly = true)
  public ColumnMappingConfig resolveMappingForHeaders(
      String platform, List<ExcelHeaderDto> uploadHeaders) {
    String normalized = normalizePlatform(platform);
    PlatformMappingTemplate entity =
        templateRepository
            .findByPlatform(normalized)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "平台「" + normalized + "」尚未配置表头模板，请先在系统配置中添加"));

    List<ExcelHeaderDto> templateHeaders = loadTemplateHeaders(entity);
    List<ColumnMappingItemDto> savedMapping = readMappingDtos(entity);

    List<ColumnMappingItemDto> rematched =
        rematchMapping(savedMapping, templateHeaders, uploadHeaders);
    List<ColumnMappingItemDto> merged =
        columnMappingService.mergePlatformMapping(
            filterPlatformMappingDtos(rematched), uploadHeaders);
    return columnMappingService.fromDtos(filterPlatformMappingDtos(merged), false);
  }

  /**
   * 新增平台（仅名称入库，模板内容后续上传保存）
   */
  @Transactional
  public PlatformTemplateDetailDto create(String platform) {
    String normalized = normalizePlatform(platform);
    if (templateRepository.existsByPlatform(normalized)) {
      throw new BusinessException("平台「" + normalized + "」已存在");
    }
    PlatformMappingTemplate entity = new PlatformMappingTemplate();
    entity.setPlatform(normalized);
    entity.setMappingJson("[]");
    entity.setTemplateHeadersJson("[]");
    templateRepository.save(entity);
    return toDetail(entity);
  }

  @Transactional
  public PlatformTemplateDetailDto save(String platform, SavePlatformTemplateRequest request) {
    String normalized = normalizePlatform(platform);
    if (request == null || request.getMapping() == null || request.getMapping().isEmpty()) {
      throw new BusinessException("请配置列映射后再保存");
    }
    if (request.getTemplateHeaders() == null || request.getTemplateHeaders().isEmpty()) {
      throw new BusinessException("请先上传模板 Excel");
    }

    List<ColumnMappingItemDto> filteredMapping = filterPlatformMappingDtos(request.getMapping());
    ColumnMappingService.PlatformTemplateHeadersResolveResult resolved =
        columnMappingService.resolvePlatformTemplateHeaders(
            request.getTemplateHeaders(), filteredMapping);
    List<ExcelHeaderDto> templateHeaders = resolved.templateHeaders();
    List<ColumnMappingItemDto> mappingToSave =
        columnMappingService.mergePlatformMapping(resolved.mapping(), templateHeaders);
    columnMappingService.fromDtos(mappingToSave, false);

    PlatformMappingTemplate entity =
        templateRepository.findByPlatform(normalized).orElseGet(PlatformMappingTemplate::new);
    entity.setPlatform(normalized);
    entity.setMappingJson(writeJson(mappingToSave));
    entity.setTemplateHeadersJson(writeJson(templateHeaders));
    entity.setTemplateFileName(request.getTemplateFileName());
    templateRepository.save(entity);
    return toDetail(entity);
  }

  @Transactional
  public void delete(String platform) {
    templateRepository.delete(getRequired(platform));
  }

  private PlatformMappingTemplate getRequired(String platform) {
    return templateRepository
        .findByPlatform(normalizePlatform(platform))
        .orElseThrow(() -> new BusinessException("平台模板不存在: " + platform));
  }

  private PlatformTemplateDetailDto toDetail(PlatformMappingTemplate entity) {
    List<ColumnMappingItemDto> savedMapping = readMappingDtos(entity);
    ColumnMappingService.PlatformTemplateHeadersResolveResult resolved =
        columnMappingService.resolvePlatformTemplateHeaders(
            readTemplateHeaders(entity), savedMapping);
    List<ColumnMappingItemDto> mergedMapping =
        columnMappingService.mergePlatformMapping(
            resolved.mapping(), resolved.templateHeaders());
    return new PlatformTemplateDetailDto(
        entity.getPlatform(),
        entity.getTemplateFileName(),
        mergedMapping,
        resolved.templateHeaders(),
        entity.getUpdatedAt());
  }

  private List<ColumnMappingItemDto> readMappingDtos(PlatformMappingTemplate entity) {
    try {
      List<ColumnMappingItemDto> dtos =
          objectMapper.readValue(
              entity.getMappingJson(), new TypeReference<List<ColumnMappingItemDto>>() {});
      return filterPlatformMappingDtos(dtos);
    } catch (Exception ex) {
      throw new BusinessException("平台模板映射数据损坏");
    }
  }

  private List<ColumnMappingItemDto> filterPlatformMappingDtos(List<ColumnMappingItemDto> dtos) {
    if (dtos == null) {
      return List.of();
    }
    return dtos.stream()
        .filter(dto -> dto.getFieldKey() != null && !"merchant".equals(dto.getFieldKey()))
        .toList();
  }

  private List<ExcelHeaderDto> readTemplateHeaders(PlatformMappingTemplate entity) {
    if (entity.getTemplateHeadersJson() == null || entity.getTemplateHeadersJson().isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          entity.getTemplateHeadersJson(), new TypeReference<List<ExcelHeaderDto>>() {});
    } catch (Exception ex) {
      throw new BusinessException("平台模板表头数据损坏");
    }
  }

  private List<ExcelHeaderDto> loadTemplateHeaders(PlatformMappingTemplate entity) {
    return columnMappingService
        .resolvePlatformTemplateHeaders(readTemplateHeaders(entity), readMappingDtos(entity))
        .templateHeaders();
  }

  private List<ColumnMappingItemDto> rematchMapping(
      List<ColumnMappingItemDto> saved,
      List<ExcelHeaderDto> templateHeaders,
      List<ExcelHeaderDto> uploadHeaders) {
    ColumnMappingConfig suggested =
        columnMappingService.suggestMappingFromHeaders(uploadHeaders);
    if (uploadHeaders.isEmpty()) {
      return saved;
    }

    Map<String, Integer> uploadIndexByNormalizedName = buildUploadHeaderIndexMap(uploadHeaders);

    List<ColumnMappingItemDto> result = new ArrayList<>();
    int order = 0;
    for (ColumnMappingItemDto item : saved) {
      ColumnMappingItemDto copy = new ColumnMappingItemDto();
      copy.setFieldKey(item.getFieldKey());
      copy.setEnabled(item.getEnabled());
      copy.setSortOrder(order++);

      String templateName = resolveHeaderNameByColumnIndex(templateHeaders, item.getSourceIndex());
      Integer newIndex = resolveUploadColumnIndex(templateName, uploadHeaders, uploadIndexByNormalizedName);
      if (newIndex == null) {
        ColumnMappingItem suggestedItem =
            suggested.getItems().stream()
                .filter(s -> s.getFieldKey().getCode().equals(item.getFieldKey()))
                .findFirst()
                .orElse(null);
        newIndex = suggestedItem == null ? -1 : suggestedItem.getSourceIndex();
      }
      copy.setSourceIndex(newIndex);
      if (newIndex == null || newIndex < 0) {
        copy.setEnabled(false);
      } else if (item.getEnabled() == null || item.getEnabled()) {
        copy.setEnabled(true);
      }
      result.add(copy);
    }
    return result;
  }

  private Map<String, Integer> buildUploadHeaderIndexMap(List<ExcelHeaderDto> uploadHeaders) {
    Map<String, Integer> uploadIndexByNormalizedName = new LinkedHashMap<>();
    for (ExcelHeaderDto header : uploadHeaders) {
      String normalized = normalizeHeaderName(header.getHeaderName());
      if (!normalized.isEmpty()) {
        uploadIndexByNormalizedName.putIfAbsent(normalized, header.getColumnIndex());
      }
    }
    return uploadIndexByNormalizedName;
  }

  private Integer resolveUploadColumnIndex(
      String templateHeaderName,
      List<ExcelHeaderDto> uploadHeaders,
      Map<String, Integer> uploadIndexByNormalizedName) {
    if (templateHeaderName == null || templateHeaderName.isBlank()) {
      return null;
    }
    String normalizedTemplateName = normalizeHeaderName(templateHeaderName);
    Integer exact = uploadIndexByNormalizedName.get(normalizedTemplateName);
    if (exact != null) {
      return exact;
    }
    for (ExcelHeaderDto header : uploadHeaders) {
      String normalizedUploadName = normalizeHeaderName(header.getHeaderName());
      if (headersSimilar(normalizedUploadName, normalizedTemplateName)) {
        return header.getColumnIndex();
      }
    }
    return null;
  }

  private String resolveHeaderNameByColumnIndex(
      List<ExcelHeaderDto> headers, Integer sourceIndex) {
    if (sourceIndex == null || sourceIndex < 0) {
      return null;
    }
    return headers.stream()
        .filter(header -> header.getColumnIndex() == sourceIndex)
        .map(ExcelHeaderDto::getHeaderName)
        .findFirst()
        .orElse(null);
  }

  private List<String> extractHeaderNames(List<ExcelHeaderDto> headers) {
    return headers.stream().map(ExcelHeaderDto::getHeaderName).toList();
  }

  private String normalizePlatform(String platform) {
    if (platform == null || platform.isBlank()) {
      throw new BusinessException("平台名称不能为空");
    }
    return platform.trim();
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new BusinessException("保存模板失败");
    }
  }

  private int scoreHeaderMatch(List<String> uploadHeaders, List<String> templateHeaders) {
    Set<String> uploadSet = new HashSet<>(uploadHeaders);
    int score = 0;
    for (String templateName : templateHeaders) {
      String normalizedTemplate = normalizeHeaderName(templateName);
      if (uploadSet.contains(normalizedTemplate)) {
        score++;
        continue;
      }
      for (String uploadName : uploadHeaders) {
        if (headersSimilar(uploadName, normalizedTemplate)) {
          score++;
          break;
        }
      }
    }
    return score;
  }

  private List<String> normalizeHeaderNames(List<String> headerNames) {
    List<String> result = new ArrayList<>();
    for (String name : headerNames) {
      String normalized = normalizeHeaderName(name);
      if (!normalized.isEmpty()) {
        result.add(normalized);
      }
    }
    return result;
  }

  private String normalizeHeaderName(String name) {
    if (name == null) {
      return "";
    }
    return name.trim().toLowerCase(Locale.ROOT);
  }

  private boolean headersSimilar(String left, String right) {
    if (left.isEmpty() || right.isEmpty()) {
      return false;
    }
    return left.equals(right) || left.contains(right) || right.contains(left);
  }
}
