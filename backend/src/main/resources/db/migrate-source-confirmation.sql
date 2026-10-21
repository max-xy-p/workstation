USE workstation;

-- 已有环境暂停写入与同步并备份后执行；初始化基准不是用户确认。
SET @source_confirm_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='external_issue_sources' AND column_name='source_version'),
    'SELECT 1', 'ALTER TABLE external_issue_sources ADD COLUMN source_version BIGINT NOT NULL DEFAULT 0');
PREPARE source_confirm_stmt FROM @source_confirm_sql;
EXECUTE source_confirm_stmt;
DEALLOCATE PREPARE source_confirm_stmt;

SET @task_confirm_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tasks' AND column_name='version'),
    'SELECT 1', 'ALTER TABLE tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0');
PREPARE task_confirm_stmt FROM @task_confirm_sql;
EXECUTE task_confirm_stmt;
DEALLOCATE PREPARE task_confirm_stmt;

SET @link_confirm_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='task_issue_links' AND column_name='confirmation_version'),
    'SELECT 1', 'ALTER TABLE task_issue_links ADD COLUMN confirmed_title VARCHAR(1000) NULL, ADD COLUMN confirmed_status VARCHAR(32) NULL, ADD COLUMN confirmed_state_reason VARCHAR(64) NULL, ADD COLUMN confirmation_version BIGINT NOT NULL DEFAULT 0, ADD COLUMN confirmed_by VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD COLUMN confirmed_at DATETIME NULL, ADD COLUMN baseline_initialized BOOLEAN NOT NULL DEFAULT FALSE');
PREPARE link_confirm_stmt FROM @link_confirm_sql;
EXECUTE link_confirm_stmt;
DEALLOCATE PREPARE link_confirm_stmt;

UPDATE task_issue_links l JOIN external_issue_sources s ON s.workspace_id=l.workspace_id AND s.id=l.source_id
SET l.confirmed_title=s.title,l.confirmed_status=s.status,l.confirmed_state_reason=s.state_reason,l.baseline_initialized=TRUE
WHERE l.baseline_initialized=FALSE;
