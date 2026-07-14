package com.ecommerce.ordersplit.service;

import java.util.List;

/**
 * 导入时表头与平台模板的解析结果（唯一匹配或需用户选择）
 *
 * @author huangxinsong
 */
public record PlatformHeaderMatchResult(
    boolean ambiguous, TemplateHeaderMatch selected, List<TemplateHeaderMatch> candidates) {

  public static PlatformHeaderMatchResult unique(TemplateHeaderMatch match) {
    return new PlatformHeaderMatchResult(false, match, List.of(match));
  }

  public static PlatformHeaderMatchResult ambiguous(List<TemplateHeaderMatch> candidates) {
    return new PlatformHeaderMatchResult(true, null, List.copyOf(candidates));
  }
}
