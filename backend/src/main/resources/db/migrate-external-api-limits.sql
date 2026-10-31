USE workstation;

SET @migration_noop_sql = 'SELECT 1';
SET @sync_runs_table = 'integration_sync_runs';

-- 升级前暂停旧执行器并备份；新旧执行器不得混合运行。
SET @api_limit_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=@sync_runs_table AND column_name='next_attempt_at'),
    @migration_noop_sql, 'ALTER TABLE integration_sync_runs ADD COLUMN next_attempt_at DATETIME(3) NULL');
PREPARE api_limit_stmt FROM @api_limit_sql;
EXECUTE api_limit_stmt;
DEALLOCATE PREPARE api_limit_stmt;

SET @api_limit_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=@sync_runs_table AND column_name='progress_json'),
    @migration_noop_sql, 'ALTER TABLE integration_sync_runs ADD COLUMN progress_json MEDIUMTEXT NULL');
PREPARE api_limit_stmt FROM @api_limit_sql;
EXECUTE api_limit_stmt;
DEALLOCATE PREPARE api_limit_stmt;

SET @api_limit_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=@sync_runs_table AND index_name='idx_sync_runs_ready'),
    @migration_noop_sql, 'CREATE INDEX idx_sync_runs_ready ON integration_sync_runs(status,next_attempt_at,id)');
PREPARE api_limit_stmt FROM @api_limit_sql;
EXECUTE api_limit_stmt;
DEALLOCATE PREPARE api_limit_stmt;
