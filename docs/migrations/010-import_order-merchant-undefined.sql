-- 增量迁移：虚拟商家展示名「待分单」更名为「未定义」（可重复执行）
USE order_split_merge;

UPDATE import_order
SET merchant = '未定义'
WHERE merchant = '待分单';
