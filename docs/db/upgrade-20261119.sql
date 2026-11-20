-- 增量迁移，保留全部业务数据；已迁移则跳过。
SET @has_trace=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_log' AND column_name='trace_id');
SET @ddl=IF(@has_trace=0,'ALTER TABLE sys_log ADD COLUMN trace_id VARCHAR(32) NULL','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
