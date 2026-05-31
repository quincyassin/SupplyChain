package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.model.ColumnMappingConfig;

/**
 * Excel 表头与平台模板的匹配结果
 *
 * @author huangxinsong
 */
public record TemplateHeaderMatch(String platform, ColumnMappingConfig mapping, int matchScore) {}
