-- 增量迁移：为 import_order 表补充售后状态字段（可重复执行）
USE order_split_merge;

SET @db = DATABASE();

SET @after_sales_status_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'after_sales_status'
);

SET @add_after_sales_status_sql = IF(
    @after_sales_status_col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN after_sales_status VARCHAR(20) NOT NULL DEFAULT ''NONE'' COMMENT ''售后状态：NONE/PENDING/COMPLETED'' AFTER after_sales_at',
    'SELECT 1'
);
PREPARE add_after_sales_status_stmt FROM @add_after_sales_status_sql;
EXECUTE add_after_sales_status_stmt;
DEALLOCATE PREPARE add_after_sales_status_stmt;

-- 历史数据：已标记售后的视为「需售后」
UPDATE import_order
SET after_sales_status = 'PENDING'
WHERE after_sales = 1
  AND (after_sales_status IS NULL OR after_sales_status = '' OR after_sales_status = 'NONE');

UPDATE import_order
SET after_sales_status = 'NONE'
WHERE (after_sales = 0 OR after_sales IS NULL)
  AND (after_sales_status IS NULL OR after_sales_status = '');

SET @after_sales_status_idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND INDEX_NAME = 'idx_import_order_after_sales_status'
);

SET @add_after_sales_status_idx_sql = IF(
    @after_sales_status_idx_exists = 0,
    'ALTER TABLE import_order ADD INDEX idx_import_order_after_sales_status (after_sales_status)',
    'SELECT 1'
);
PREPARE add_after_sales_status_idx_stmt FROM @add_after_sales_status_idx_sql;
EXECUTE add_after_sales_status_idx_stmt;
DEALLOCATE PREPARE add_after_sales_status_idx_stmt;
