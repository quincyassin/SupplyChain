-- 导出配置：支持自定义导出根目录（可重复执行）

SET @export_settings_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'export_settings'
);

SET @add_export_directory_sql = IF(
    @export_settings_table_exists = 0,
    'SELECT ''export_settings 表不存在，跳过'' AS message',
    IF(
        (
            SELECT COUNT(*)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'export_settings'
              AND COLUMN_NAME = 'export_directory'
        ) = 0,
        'ALTER TABLE export_settings ADD COLUMN export_directory VARCHAR(512) NULL COMMENT ''导出根目录（绝对路径）'' AFTER mode',
        'SELECT ''export_directory 已存在，跳过'' AS message'
    )
);

PREPARE add_export_directory_stmt FROM @add_export_directory_sql;
EXECUTE add_export_directory_stmt;
DEALLOCATE PREPARE add_export_directory_stmt;
