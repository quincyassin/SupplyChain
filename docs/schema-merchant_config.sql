-- 商家配置（系统配置 → 商家配置）
CREATE TABLE IF NOT EXISTS merchant_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '商家名称',
    keywords_json TEXT NOT NULL COMMENT '商品名称匹配关键字 JSON 数组',
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE展示 HIDDEN手工维护不展示',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_merchant_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家关键字分单配置';
