-- 增量迁移：为已有 import_order 表补充回单相关字段（可重复执行）
USE order_split_merge;

SET @db = DATABASE();

SET @receipt_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'receipt_status'
);

SET @add_receipt_status_sql = IF(
    @receipt_col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN receipt_status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''回单状态：PENDING未回单 RECEIPTED已回单'' AFTER shipping_fee',
    'SELECT 1'
);
PREPARE add_receipt_status_stmt FROM @add_receipt_status_sql;
EXECUTE add_receipt_status_stmt;
DEALLOCATE PREPARE add_receipt_status_stmt;

SET @logistics_no_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'logistics_no'
);

SET @add_logistics_no_sql = IF(
    @logistics_no_col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN logistics_no VARCHAR(128) NULL COMMENT ''物流单号'' AFTER receipt_status',
    'SELECT 1'
);
PREPARE add_logistics_no_stmt FROM @add_logistics_no_sql;
EXECUTE add_logistics_no_stmt;
DEALLOCATE PREPARE add_logistics_no_stmt;

SET @logistics_company_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND COLUMN_NAME = 'logistics_company'
);

SET @add_logistics_company_sql = IF(
    @logistics_company_col_exists = 0,
    'ALTER TABLE import_order ADD COLUMN logistics_company VARCHAR(128) NULL COMMENT ''物流公司'' AFTER logistics_no',
    'SELECT 1'
);
PREPARE add_logistics_company_stmt FROM @add_logistics_company_sql;
EXECUTE add_logistics_company_stmt;
DEALLOCATE PREPARE add_logistics_company_stmt;

SET @receipt_idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'import_order'
      AND INDEX_NAME = 'idx_import_order_receipt_status'
);

SET @add_receipt_idx_sql = IF(
    @receipt_idx_exists = 0,
    'ALTER TABLE import_order ADD INDEX idx_import_order_receipt_status (receipt_status)',
    'SELECT 1'
);
PREPARE add_receipt_idx_stmt FROM @add_receipt_idx_sql;
EXECUTE add_receipt_idx_stmt;
DEALLOCATE PREPARE add_receipt_idx_stmt;
