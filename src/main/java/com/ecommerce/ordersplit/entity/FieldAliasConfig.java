package com.ecommerce.ordersplit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 系统字段别名配置（用于 Excel 表头智能匹配）
 *
 * @author huangxinsong
 */
@Entity
@Table(name = "field_alias_config")
@Getter
@Setter
public class FieldAliasConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 系统字段 key，对应 OrderFieldKey.code */
    @Column(name = "field_key", nullable = false, unique = true, length = 32)
    private String fieldKey;

    /** 别名 JSON 数组 */
    @Column(name = "aliases_json", nullable = false, columnDefinition = "TEXT")
    private String aliasesJson;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
