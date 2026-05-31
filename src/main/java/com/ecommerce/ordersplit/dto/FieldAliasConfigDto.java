package com.ecommerce.ordersplit.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 字段别名配置
 *
 * @author huangxinsong
 */
@Data
public class FieldAliasConfigDto {

    private String fieldKey;

    private String label;

    private List<String> aliases;

    private LocalDateTime updatedAt;
}
