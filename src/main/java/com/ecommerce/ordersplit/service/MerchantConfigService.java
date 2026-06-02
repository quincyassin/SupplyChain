package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.MerchantConfigDto;
import com.ecommerce.ordersplit.dto.ReassignPendingOrdersResult;
import com.ecommerce.ordersplit.dto.SaveMerchantConfigRequest;
import com.ecommerce.ordersplit.entity.MerchantConfig;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.MerchantConfigVisibility;
import com.ecommerce.ordersplit.repository.MerchantConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商家配置服务（按商品名称关键字分单）
 *
 * @author huangxinsong
 */
@Service
public class MerchantConfigService {

  public static final String UNMATCHED_MERCHANT_NAME = "未匹配";

  /** 导入后尚未匹配到商家关键字时的展示名（虚拟商家） */
  public static final String PENDING_SPLIT_MERCHANT = "未定义";

  private final MerchantConfigRepository merchantConfigRepository;
  private final ImportOrderPersistenceService importOrderPersistenceService;
  private final ObjectMapper objectMapper;

  public MerchantConfigService(
      MerchantConfigRepository merchantConfigRepository,
      @Lazy ImportOrderPersistenceService importOrderPersistenceService,
      ObjectMapper objectMapper) {
    this.merchantConfigRepository = merchantConfigRepository;
    this.importOrderPersistenceService = importOrderPersistenceService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<MerchantConfigDto> listAll() {
    return merchantConfigRepository
        .findAllByVisibilityOrderByUpdatedAtDesc(MerchantConfigVisibility.VISIBLE)
        .stream()
        .map(this::toDto)
        .toList();
  }

  /**
   * 表格手工指定商家时，确保 merchant_config 中存在对应记录（HIDDEN，不在配置页展示）
   */
  @Transactional
  public void ensureManualMerchant(String merchantName) {
    String name = normalizeName(merchantName);
    if (PENDING_SPLIT_MERCHANT.equals(name) || UNMATCHED_MERCHANT_NAME.equals(name)) {
      return;
    }
    if (merchantConfigRepository.existsByName(name)) {
      return;
    }
    MerchantConfig entity = new MerchantConfig();
    entity.setName(name);
    entity.setKeywordsJson(writeKeywords(List.of()));
    entity.setVisibility(MerchantConfigVisibility.HIDDEN);
    merchantConfigRepository.save(entity);
  }

  /**
   * 根据商品名称匹配商家（最长关键字优先）
   */
  @Transactional(readOnly = true)
  public String resolveByProductName(String productName) {
    String text = productName == null ? "" : productName.trim();
    if (text.isEmpty()) {
      return UNMATCHED_MERCHANT_NAME;
    }

    String matchedName = null;
    int bestKeywordLength = 0;
    for (MerchantConfig config : merchantConfigRepository.findAllByOrderByNameAsc()) {
      for (String keyword : readKeywords(config)) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
          continue;
        }
        if (containsIgnoreCase(text, normalizedKeyword)
            && normalizedKeyword.length() > bestKeywordLength) {
          matchedName = config.getName();
          bestKeywordLength = normalizedKeyword.length();
        }
      }
    }
    return matchedName == null ? UNMATCHED_MERCHANT_NAME : matchedName;
  }

  @Transactional
  public MerchantConfigDto create(SaveMerchantConfigRequest request) {
    String name = normalizeName(request == null ? null : request.getName());
    List<String> keywords = normalizeKeywords(request == null ? null : request.getKeywords());
    Optional<MerchantConfig> existing = merchantConfigRepository.findByName(name);
    if (existing.isPresent()) {
      MerchantConfig entity = existing.get();
      if (entity.getVisibility() == MerchantConfigVisibility.VISIBLE) {
        throw new BusinessException("商家「" + name + "」已存在");
      }
      entity.setKeywordsJson(writeKeywords(keywords));
      entity.setVisibility(MerchantConfigVisibility.VISIBLE);
      merchantConfigRepository.save(entity);
      return toDtoWithReassign(entity);
    }
    MerchantConfig entity = new MerchantConfig();
    entity.setName(name);
    entity.setKeywordsJson(writeKeywords(keywords));
    entity.setVisibility(MerchantConfigVisibility.VISIBLE);
    merchantConfigRepository.save(entity);
    return toDtoWithReassign(entity);
  }

  @Transactional
  public MerchantConfigDto update(Long id, SaveMerchantConfigRequest request) {
    MerchantConfig entity = getRequiredVisible(id);
    String name = normalizeName(request == null ? null : request.getName());
    List<String> keywords = normalizeKeywords(request == null ? null : request.getKeywords());
    if (!entity.getName().equals(name)) {
      Optional<MerchantConfig> nameConflict = merchantConfigRepository.findByName(name);
      if (nameConflict.isPresent()) {
        MerchantConfig other = nameConflict.get();
        if (other.getVisibility() == MerchantConfigVisibility.VISIBLE) {
          throw new BusinessException("商家「" + name + "」已存在");
        }
        merchantConfigRepository.delete(other);
      }
    }
    entity.setName(name);
    entity.setKeywordsJson(writeKeywords(keywords));
    merchantConfigRepository.save(entity);
    return toDto(entity);
  }

  @Transactional
  public void delete(Long id) {
    merchantConfigRepository.delete(getRequiredVisible(id));
  }

  private MerchantConfig getRequiredVisible(Long id) {
    MerchantConfig entity = getRequired(id);
    if (entity.getVisibility() != MerchantConfigVisibility.VISIBLE) {
      throw new BusinessException("商家配置不存在");
    }
    return entity;
  }

  private MerchantConfig getRequired(Long id) {
    return merchantConfigRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException("商家配置不存在"));
  }

  private MerchantConfigDto toDto(MerchantConfig entity) {
    MerchantConfigDto dto = new MerchantConfigDto();
    dto.setId(entity.getId());
    dto.setName(entity.getName());
    dto.setKeywords(readKeywords(entity));
    dto.setUpdatedAt(entity.getUpdatedAt());
    return dto;
  }

  private MerchantConfigDto toDtoWithReassign(MerchantConfig entity) {
    ReassignPendingOrdersResult reassign =
        importOrderPersistenceService.reassignAllPendingOrders();
    MerchantConfigDto dto = toDto(entity);
    dto.setReassignedScannedCount(reassign.scannedOrderCount());
    dto.setReassignedMatchedCount(reassign.matchedOrderCount());
    dto.setReassignedStillPendingCount(reassign.stillPendingOrderCount());
    return dto;
  }

  private List<String> readKeywords(MerchantConfig entity) {
    try {
      List<String> keywords =
          objectMapper.readValue(entity.getKeywordsJson(), new TypeReference<List<String>>() {});
      return keywords == null ? List.of() : keywords;
    } catch (Exception ex) {
      throw new BusinessException("商家关键字数据损坏");
    }
  }

  private List<String> normalizeKeywords(List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      throw new BusinessException("请至少配置一个关键字");
    }
    List<String> result = new ArrayList<>();
    for (String keyword : keywords) {
      if (keyword == null) {
        continue;
      }
      String trimmed = keyword.trim();
      if (!trimmed.isEmpty() && !result.contains(trimmed)) {
        result.add(trimmed);
      }
    }
    if (result.isEmpty()) {
      throw new BusinessException("请至少配置一个有效关键字");
    }
    return result;
  }

  private String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new BusinessException("商家名称不能为空");
    }
    return name.trim();
  }

  private String writeKeywords(List<String> keywords) {
    try {
      return objectMapper.writeValueAsString(keywords);
    } catch (Exception ex) {
      throw new BusinessException("保存关键字失败");
    }
  }

  private boolean containsIgnoreCase(String text, String keyword) {
    return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
  }
}
