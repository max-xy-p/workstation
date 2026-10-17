USE workstation;

-- 新安装在 schema.sql 后执行；升级已完成 Workspace 迁移的数据库时先备份。
DROP PROCEDURE IF EXISTS migrate_project_integrations;
DELIMITER $$
CREATE PROCEDURE migrate_project_integrations()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'homepage_url'
    ) THEN
        ALTER TABLE projects ADD COLUMN homepage_url VARCHAR(500) NULL AFTER repository_url;
    END IF;
END$$
DELIMITER ;
CALL migrate_project_integrations();
DROP PROCEDURE migrate_project_integrations;

CREATE TABLE IF NOT EXISTS integration_connections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    provider VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account VARCHAR(255) NOT NULL,
    credential TEXT NULL,
    key_version VARCHAR(32) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    include_forks BOOLEAN NOT NULL DEFAULT FALSE,
    include_archived BOOLEAN NOT NULL DEFAULT FALSE,
    auto_import BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_minutes INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL DEFAULT 1,
    owner_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_error VARCHAR(500) NULL,
    last_synced_at DATETIME NULL,
    next_sync_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connections_workspace_id (workspace_id, id),
    INDEX idx_connections_due (enabled, deleted, next_sync_at),
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    FOREIGN KEY (owner_subject) REFERENCES security_principals (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS external_repositories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    connection_id BIGINT NULL,
    provider VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    namespace VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    description TEXT NULL,
    default_branch VARCHAR(255) NULL,
    language VARCHAR(255) NULL,
    visibility VARCHAR(32) NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    fork BOOLEAN NOT NULL DEFAULT FALSE,
    auto_import_suppressed BOOLEAN NOT NULL DEFAULT FALSE,
    last_pushed_at DATETIME NULL,
    last_synced_at DATETIME NULL,
    last_error VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_repository_source (workspace_id, provider, base_url, external_id),
    UNIQUE KEY uk_repository_workspace_id (workspace_id, id),
    INDEX idx_repository_connection (workspace_id, connection_id, id),
    FOREIGN KEY (workspace_id, connection_id) REFERENCES integration_connections (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_repositories (
    workspace_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    PRIMARY KEY (workspace_id, repository_id),
    INDEX idx_project_repositories (workspace_id, project_id),
    FOREIGN KEY (workspace_id, project_id) REFERENCES projects (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, repository_id) REFERENCES external_repositories (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS integration_sync_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    connection_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    config_version INT NOT NULL,
    processed INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36) NULL,
    lease_until DATETIME NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_request (workspace_id, connection_id, actor_subject, request_key),
    UNIQUE KEY uk_run_workspace_id (workspace_id, id),
    INDEX idx_run_queue (status, lease_until, id),
    FOREIGN KEY (workspace_id, connection_id) REFERENCES integration_connections (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS integration_sync_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    project_status VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    last_error VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sync_item (run_id, repository_id),
    FOREIGN KEY (workspace_id, run_id) REFERENCES integration_sync_runs (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, repository_id) REFERENCES external_repositories (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS external_development_snapshots (
    workspace_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    available BOOLEAN NOT NULL,
    truncated BOOLEAN NOT NULL,
    message VARCHAR(500) NULL,
    since DATETIME NOT NULL,
    synced_at DATETIME NOT NULL,
    collected INT NOT NULL,
    open_count INT NOT NULL,
    PRIMARY KEY (workspace_id, repository_id, kind),
    FOREIGN KEY (workspace_id, repository_id) REFERENCES external_repositories (workspace_id, id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS external_development_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    external_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(1000) NOT NULL,
    url VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL,
    raw_status VARCHAR(64) NULL,
    author VARCHAR(255) NULL,
    branch VARCHAR(255) NULL,
    source_updated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_development_source (workspace_id, repository_id, kind, external_id),
    INDEX idx_development_recent (workspace_id, kind, source_updated_at),
    INDEX idx_development_repository (workspace_id, repository_id, kind, source_updated_at, id),
    FOREIGN KEY (workspace_id, repository_id) REFERENCES external_repositories (workspace_id, id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
