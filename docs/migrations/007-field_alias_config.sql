-- 字段别名配置表（可重复执行）
USE order_split_merge;

CREATE TABLE IF NOT EXISTS field_alias_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    field_key VARCHAR(32) NOT NULL COMMENT '系统字段 key',
    aliases_json TEXT NOT NULL COMMENT '别名 JSON 数组',
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_field_alias_config_field_key (field_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字段别名（表头智能匹配）';
