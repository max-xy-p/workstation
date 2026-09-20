USE workstation;

-- Workspace 迁移完成后的审计异步化迁移脚本。
-- 执行前必须备份数据库；本脚本不会由已有数据卷自动执行。

ALTER TABLE audit_events
    ADD COLUMN event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER id;

UPDATE audit_events SET event_id = UUID() WHERE event_id IS NULL;

ALTER TABLE audit_events
    MODIFY event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uk_audit_events_event_id (event_id);

CREATE TABLE audit_outbox (
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
