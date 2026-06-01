-- 商品价格表（平台 + 商品名称 + 规格 + 成本价 + 供货价）

CREATE TABLE IF NOT EXISTS product_price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(128) NOT NULL COMMENT '平台',
    product_name VARCHAR(255) NOT NULL COMMENT '商品名称',
    spec VARCHAR(128) NOT NULL DEFAULT '' COMMENT '规格',
    cost_price DECIMAL(12, 2) NULL COMMENT '成本价',
    supply_price DECIMAL(12, 2) NULL COMMENT '供货价',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_price_key (platform, product_name, spec)
) COMMENT '商品价格（平台 + 商品名称 + 规格）';
