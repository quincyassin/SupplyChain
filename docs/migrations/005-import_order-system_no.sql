-- 增量迁移：为 import_order 表补充 system_no 列（可重复执行）
USE order_split_merge;

SET @db = DATABASE();

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'system_no'
);

SET @add_col_sql = IF(
    @col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN system_no VARCHAR(36) NULL COMMENT ''系统编号（16位 NanoId）'' AFTER order_no',
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
      AND INDEX_NAME = 'uk_import_order_system_no'
);

SET @add_idx_sql = IF(
    @idx_exists = 0,
    'ALTER TABLE import_order ADD UNIQUE INDEX uk_import_order_system_no (system_no)',
    'SELECT 1'
);
PREPARE add_idx_stmt FROM @add_idx_sql;
EXECUTE add_idx_stmt;
DEALLOCATE PREPARE add_idx_stmt;
