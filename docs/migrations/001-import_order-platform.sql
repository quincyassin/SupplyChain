-- 增量迁移：为已有 import_order 表补充 platform 列与索引（可重复执行）
USE order_split_merge;

SET @db = DATABASE();

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'platform'
);

SET @add_col_sql = IF(
    @col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN platform VARCHAR(128) NULL COMMENT ''导入时匹配的平台模板'' AFTER merchant',
    'SELECT 1'
);
PREPARE add_col_stmt FROM @add_col_sql;
EXECUTE add_col_stmt;
DEALLOCATE PREPARE add_col_stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND INDEX_NAME = 'idx_import_order_platform'
);

SET @add_idx_sql = IF(
    @idx_exists = 0,
    'ALTER TABLE import_order ADD INDEX idx_import_order_platform (platform)',
    'SELECT 1'
);
PREPARE add_idx_stmt FROM @add_idx_sql;
EXECUTE add_idx_stmt;
DEALLOCATE PREPARE add_idx_stmt;
