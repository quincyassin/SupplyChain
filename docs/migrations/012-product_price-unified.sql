-- 合并 product_cost_price / product_supply_price 为 product_price

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

INSERT INTO product_price (platform, product_name, spec, cost_price, supply_price, updated_at)
SELECT s.platform,
       s.product_name,
       s.spec,
       c.cost_price,
       s.supply_price,
       GREATEST(s.updated_at, IFNULL(c.updated_at, s.updated_at))
FROM product_supply_price s
LEFT JOIN product_cost_price c
    ON c.product_name = s.product_name AND c.spec = s.spec
WHERE NOT EXISTS (
    SELECT 1 FROM product_price p
    WHERE p.platform = s.platform
      AND p.product_name = s.product_name
      AND p.spec = s.spec
);

DROP TABLE IF EXISTS product_supply_price;
DROP TABLE IF EXISTS product_cost_price;
