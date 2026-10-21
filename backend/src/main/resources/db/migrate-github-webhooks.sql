USE workstation;

-- 在维护窗口备份并暂停同步后执行；新安装复用同一结构，重复执行不改写已有数据。
CREATE TABLE IF NOT EXISTS integration_webhooks (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    connection_id BIGINT NOT NULL,
    secret TEXT NOT NULL,
    key_version VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    version INT NOT NULL,
    UNIQUE KEY uk_webhook_connection (workspace_id,connection_id),
    FOREIGN KEY (workspace_id,connection_id) REFERENCES integration_connections(workspace_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS github_webhook_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    webhook_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workspace_id BIGINT NOT NULL,
    connection_id BIGINT NOT NULL,
    webhook_version INT NOT NULL,
    delivery_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    body_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    action VARCHAR(64) NULL,
    source_id BIGINT NULL,
    link_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    run_id BIGINT NULL,
    last_error VARCHAR(1000) NULL,
    received_at DATETIME NOT NULL,
    next_attempt_at DATETIME NOT NULL,
    UNIQUE KEY uk_webhook_delivery (webhook_id,delivery_key),
    INDEX idx_webhook_pending (workspace_id,connection_id,status,next_attempt_at,id),
    FOREIGN KEY (webhook_id) REFERENCES integration_webhooks(id),
    FOREIGN KEY (workspace_id,source_id) REFERENCES external_issue_sources(workspace_id,id),
    FOREIGN KEY (workspace_id,run_id) REFERENCES integration_sync_runs(workspace_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS integration_issue_targets (
    workspace_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    link_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    last_error VARCHAR(1000) NULL,
    PRIMARY KEY (workspace_id,run_id,source_id),
    FOREIGN KEY (workspace_id,run_id) REFERENCES integration_sync_runs(workspace_id,id),
    FOREIGN KEY (workspace_id,source_id) REFERENCES external_issue_sources(workspace_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @webhook_run_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='integration_sync_runs' AND column_name='webhook_id'),
    'SELECT 1','ALTER TABLE integration_sync_runs ADD COLUMN webhook_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD COLUMN webhook_version INT NULL');
PREPARE webhook_run_stmt FROM @webhook_run_sql;
EXECUTE webhook_run_stmt;
DEALLOCATE PREPARE webhook_run_stmt;
