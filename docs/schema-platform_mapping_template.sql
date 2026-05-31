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
