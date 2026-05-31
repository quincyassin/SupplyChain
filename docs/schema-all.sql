-- 全量建表脚本（手工初始化或已有库补表）
-- 用法: mysql -h127.0.0.1 -uroot -p order_split_merge < docs/schema-all.sql

CREATE DATABASE IF NOT EXISTS order_split_merge
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE order_split_merge;

-- 平台表头模板（系统配置 → 表头映射）
CREATE TABLE IF NOT EXISTS platform_mapping_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(128) NOT NULL COMMENT '平台名称，如淘宝、拼多多',
    mapping_json TEXT NOT NULL COMMENT '列映射 JSON',
    template_headers_json TEXT COMMENT '模板表头 JSON',
    template_file_name VARCHAR(255) COMMENT '模板文件名',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台导入表头模板';

-- 商家配置（系统配置 → 商家配置）
CREATE TABLE IF NOT EXISTS merchant_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '商家名称',
    keywords_json TEXT NOT NULL COMMENT '商品名称匹配关键字 JSON 数组',
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE展示 HIDDEN手工维护不展示',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_merchant_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家关键字分单配置';

-- 分单处理任务
CREATE TABLE IF NOT EXISTS process_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL COMMENT '原始上传文件名',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型：SPLIT 等',
    status VARCHAR(20) NOT NULL COMMENT '任务状态',
    message VARCHAR(500) COMMENT '结果说明',
    input_row_count INT COMMENT '输入行数',
    merchant_group_count INT COMMENT '商家分组数',
    output_row_count INT COMMENT '输出行数',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_process_task_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分单处理任务';

-- 导入分单订单明细
CREATE TABLE IF NOT EXISTS import_order (
    system_no VARCHAR(20) NOT NULL PRIMARY KEY COMMENT '系统编号（10位雪花ID，主键）',
    task_id BIGINT NOT NULL COMMENT '分单任务ID，关联 process_task.id',
    merchant VARCHAR(128) NOT NULL COMMENT '商家',
    platform VARCHAR(128) COMMENT '导入时匹配的平台模板',
    order_no VARCHAR(64) COMMENT '订单编号',
    product_name VARCHAR(255) COMMENT '名称',
    spec VARCHAR(128) COMMENT '规格',
    quantity INT COMMENT '数量',
    receiver VARCHAR(64) COMMENT '收货人',
    address VARCHAR(512) COMMENT '收货地址',
    phone VARCHAR(32) COMMENT '手机号',
    shipping_fee DECIMAL(12, 2) COMMENT '运费',
    remark VARCHAR(512) COMMENT '备注',
    cost_price DECIMAL(12, 2) COMMENT '成本价',
    supply_price DECIMAL(12, 2) COMMENT '供货价',
    receipt_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '回单状态：PENDING未回单 RECEIPTED已回单',
    logistics_no VARCHAR(128) COMMENT '物流单号',
    logistics_company VARCHAR(128) COMMENT '物流公司',
    after_sales TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需售后',
    after_sales_remark VARCHAR(512) COMMENT '售后原因备注',
    after_sales_at DATETIME COMMENT '标记售后时间',
    issue_date DATETIME NOT NULL COMMENT '发单日期',
    source_row_num INT COMMENT 'Excel原始行号',
    created_at DATETIME NOT NULL COMMENT '入库时间',
    INDEX idx_import_order_task_id (task_id),
    INDEX idx_import_order_merchant (merchant),
    INDEX idx_import_order_platform (platform),
    INDEX idx_import_order_issue_date (issue_date),
    INDEX idx_import_order_receipt_status (receipt_status),
    INDEX idx_import_order_after_sales (after_sales)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入分单订单明细';
