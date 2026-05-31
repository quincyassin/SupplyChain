-- 增量迁移：为 import_order 表补充售后标记字段（可重复执行）
USE order_split_merge;

SET @db = DATABASE();

SET @after_sales_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'after_sales'
);

SET @add_after_sales_sql = IF(
    @after_sales_col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN after_sales TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否需售后'' AFTER logistics_company',
    'SELECT 1'
);
PREPARE add_after_sales_stmt FROM @add_after_sales_sql;
EXECUTE add_after_sales_stmt;
DEALLOCATE PREPARE add_after_sales_stmt;

SET @after_sales_remark_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'after_sales_remark'
);

SET @add_after_sales_remark_sql = IF(
    @after_sales_remark_col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN after_sales_remark VARCHAR(512) NULL COMMENT ''售后原因备注'' AFTER after_sales',
    'SELECT 1'
);
PREPARE add_after_sales_remark_stmt FROM @add_after_sales_remark_sql;
EXECUTE add_after_sales_remark_stmt;
DEALLOCATE PREPARE add_after_sales_remark_stmt;

SET @after_sales_at_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'after_sales_at'
);

SET @add_after_sales_at_sql = IF(
    @after_sales_at_col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN after_sales_at DATETIME NULL COMMENT ''标记售后时间'' AFTER after_sales_remark',
    'SELECT 1'
);
PREPARE add_after_sales_at_stmt FROM @add_after_sales_at_sql;
EXECUTE add_after_sales_at_stmt;
DEALLOCATE PREPARE add_after_sales_at_stmt;

SET @after_sales_idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND INDEX_NAME = 'idx_import_order_after_sales'
);

SET @add_after_sales_idx_sql = IF(
    @after_sales_idx_exists = 0,
    'ALTER TABLE import_order ADD INDEX idx_import_order_after_sales (after_sales)',
    'SELECT 1'
);
PREPARE add_after_sales_idx_stmt FROM @add_after_sales_idx_sql;
EXECUTE add_after_sales_idx_stmt;
DEALLOCATE PREPARE add_after_sales_idx_stmt;

-- 历史数据统一视为「无需售后」，仅手动标记后才为 1
UPDATE import_order
SET after_sales = 0
WHERE after_sales IS NULL;
