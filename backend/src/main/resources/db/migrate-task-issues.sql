USE workstation;

-- 已有数据库须暂停写入并备份；所有新增结构可重复执行，不自动转化历史任务。
SET @task_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='tasks' AND index_name='uk_tasks_workspace_id_id'),
    'SELECT 1', 'ALTER TABLE tasks ADD UNIQUE KEY uk_tasks_workspace_id_id (workspace_id,id)');
PREPARE task_index_stmt FROM @task_index_sql;
EXECUTE task_index_stmt;
DEALLOCATE PREPARE task_index_stmt;

SET @issue_fields_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='external_development_items' AND column_name='issue_number'),
    'SELECT 1', 'ALTER TABLE external_development_items ADD COLUMN issue_number BIGINT NULL, ADD COLUMN state_reason VARCHAR(64) NULL');
PREPARE issue_fields_stmt FROM @issue_fields_sql;
EXECUTE issue_fields_stmt;
DEALLOCATE PREPARE issue_fields_stmt;

CREATE TABLE IF NOT EXISTS external_issue_sources (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    provider VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issue_number BIGINT NOT NULL,
    title VARCHAR(1000) NOT NULL,
    url VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL,
    state_reason VARCHAR(64) NULL,
    source_updated_at DATETIME NULL,
    last_attempt_at DATETIME NULL,
    last_success_at DATETIME NULL,
    last_error VARCHAR(1000) NULL,
    UNIQUE KEY uk_issue_identity (workspace_id,provider,base_url,external_id),
    UNIQUE KEY uk_issue_workspace_id (workspace_id,id),
    FOREIGN KEY (workspace_id,repository_id) REFERENCES external_repositories(workspace_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS task_issue_links (
    workspace_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    link_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (workspace_id,task_id),
    UNIQUE KEY uk_task_issue_source (workspace_id,source_id),
    FOREIGN KEY (workspace_id,task_id) REFERENCES tasks(workspace_id,id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id,source_id) REFERENCES external_issue_sources(workspace_id,id),
    FOREIGN KEY (actor_subject) REFERENCES security_principals(subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @issue_cursor_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='integration_sync_runs' AND column_name='issue_cursor'),
    'SELECT 1', 'ALTER TABLE integration_sync_runs ADD COLUMN issue_cursor BIGINT NOT NULL DEFAULT 0, ADD COLUMN issue_upper_bound BIGINT NULL');
PREPARE issue_cursor_stmt FROM @issue_cursor_sql;
EXECUTE issue_cursor_stmt;
DEALLOCATE PREPARE issue_cursor_stmt;
