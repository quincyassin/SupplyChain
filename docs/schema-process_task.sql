-- 分单处理任务表
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
