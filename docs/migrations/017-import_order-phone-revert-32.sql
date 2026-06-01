-- 若已执行过 016，将 phone 恢复为 VARCHAR(32)；导入超长时在应用层报错
ALTER TABLE import_order
    MODIFY COLUMN phone VARCHAR(32) COMMENT '手机号';

ALTER TABLE import_order_archive
    MODIFY COLUMN phone VARCHAR(32) COMMENT '手机号';
