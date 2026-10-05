USE workstation;

-- 外部数据表定义位于本文件末尾。

CREATE TABLE IF NOT EXISTS security_principals (
    subject_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    principal_type VARCHAR(32) NOT NULL,
    username VARCHAR(255) NULL,
    display_name VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (subject_id),
    INDEX idx_security_principals_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS workspaces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(32) NOT NULL,
    personal_owner_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_by_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspaces_personal_owner (personal_owner_subject),
    INDEX idx_workspaces_created_by (created_by_subject),
    CONSTRAINT fk_workspaces_created_by
        FOREIGN KEY (created_by_subject) REFERENCES security_principals (subject_id),
    CONSTRAINT fk_workspaces_personal_owner
        FOREIGN KEY (personal_owner_subject) REFERENCES security_principals (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS workspace_members (
    workspace_id BIGINT NOT NULL,
    principal_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at DATETIME NOT NULL,
    PRIMARY KEY (workspace_id, principal_subject),
    INDEX idx_workspace_members_principal (principal_subject, workspace_id),
    CONSTRAINT fk_workspace_members_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_members_principal
        FOREIGN KEY (principal_subject) REFERENCES security_principals (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL,
    repository_url VARCHAR(500) NULL,
    homepage_url VARCHAR(500) NULL,
    tech_stack VARCHAR(500) NULL,
    run_guide TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_workspace_id_id (workspace_id, id),
    INDEX idx_projects_workspace_id (workspace_id),
    INDEX idx_projects_status (status),
    INDEX idx_projects_updated_at (updated_at),
    CONSTRAINT fk_projects_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS note_folders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    parent_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_folders_workspace_id_id (workspace_id, id),
    INDEX idx_note_folders_workspace_parent (workspace_id, parent_id),
    UNIQUE KEY uk_note_folders_workspace_parent_name (workspace_id, parent_id, name),
    CONSTRAINT fk_note_folders_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_note_folders_parent
        FOREIGN KEY (workspace_id, parent_id) REFERENCES note_folders (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tags_workspace_id_id (workspace_id, id),
    UNIQUE KEY uk_tags_workspace_name (workspace_id, name),
    CONSTRAINT fk_tags_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    folder_id BIGINT NULL,
    project_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    deleted_at DATETIME NULL,
    deleted_by_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notes_workspace_id_id (workspace_id, id),
    INDEX idx_notes_workspace_id (workspace_id),
    INDEX idx_notes_workspace_folder (workspace_id, folder_id),
    INDEX idx_notes_workspace_project (workspace_id, project_id),
    INDEX idx_notes_workspace_deleted (workspace_id, deleted_at),
    INDEX idx_notes_updated_at (updated_at),
    CONSTRAINT fk_notes_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_notes_folder
        FOREIGN KEY (workspace_id, folder_id) REFERENCES note_folders (workspace_id, id),
    CONSTRAINT fk_notes_project
        FOREIGN KEY (workspace_id, project_id) REFERENCES projects (workspace_id, id),
    CONSTRAINT fk_notes_deleted_by
        FOREIGN KEY (deleted_by_subject) REFERENCES security_principals (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    project_id BIGINT NOT NULL,
    evidence_note_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    due_date DATE NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_tasks_workspace_id (workspace_id),
    INDEX idx_tasks_workspace_project (workspace_id, project_id),
    INDEX idx_tasks_workspace_evidence (workspace_id, evidence_note_id),
    INDEX idx_tasks_status (status),
    INDEX idx_tasks_priority (priority),
    CONSTRAINT fk_tasks_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (workspace_id, project_id) REFERENCES projects (workspace_id, id),
    CONSTRAINT fk_tasks_evidence_note
        FOREIGN KEY (workspace_id, evidence_note_id) REFERENCES notes (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS note_tag (
    workspace_id BIGINT NOT NULL,
    note_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (workspace_id, note_id, tag_id),
    INDEX idx_note_tag_workspace_tag (workspace_id, tag_id),
    CONSTRAINT fk_note_tag_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_note_tag_note
        FOREIGN KEY (workspace_id, note_id) REFERENCES notes (workspace_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_note_tag_tag
        FOREIGN KEY (workspace_id, tag_id) REFERENCES tags (workspace_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workspace_id BIGINT NOT NULL,
    actor_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id BIGINT NULL,
    result VARCHAR(32) NOT NULL,
    occurred_at DATETIME NOT NULL,
    trace_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    locked_by VARCHAR(128) NULL,
    locked_until DATETIME NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    published_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_outbox_event_id (event_id),
    INDEX idx_audit_outbox_pending (status, next_attempt_at, id),
    INDEX idx_audit_outbox_lock (status, locked_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workspace_id BIGINT NOT NULL,
    actor_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id BIGINT NULL,
    result VARCHAR(32) NOT NULL,
    occurred_at DATETIME NOT NULL,
    trace_id VARCHAR(128) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_events_event_id (event_id),
    INDEX idx_audit_workspace_time (workspace_id, occurred_at, id),
    INDEX idx_audit_workspace_actor (workspace_id, actor_subject),
    INDEX idx_audit_workspace_action (workspace_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
