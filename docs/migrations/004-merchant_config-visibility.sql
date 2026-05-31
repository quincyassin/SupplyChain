-- 增量迁移：商家配置可见性（可重复执行）
USE order_split_merge;

SET @db = DATABASE();

SET @visibility_col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'merchant_config'
      AND COLUMN_NAME = 'visibility'
);

SET @add_visibility_sql = IF(
    @visibility_col_exists = 0,
    'ALTER TABLE merchant_config ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT ''VISIBLE'' COMMENT ''VISIBLE展示 HIDDEN手工维护不展示'' AFTER keywords_json',
    'SELECT 1'
);
PREPARE add_visibility_stmt FROM @add_visibility_sql;
EXECUTE add_visibility_stmt;
DEALLOCATE PREPARE add_visibility_stmt;

UPDATE merchant_config
SET visibility = 'VISIBLE'
WHERE visibility IS NULL OR visibility = '';
