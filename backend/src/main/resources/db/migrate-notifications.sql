-- 新建与升级前应备份业务库；通知直接与业务写入同一事务。
USE workstation;

CREATE TABLE IF NOT EXISTS notification_events (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    workspace_id BIGINT NOT NULL,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_notification_events_retention (created_at),
    CONSTRAINT fk_notification_event_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_inboxes (
    recipient VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    signal_pending BOOLEAN NOT NULL DEFAULT FALSE,
    signal_next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    signal_lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    PRIMARY KEY (recipient),
    INDEX idx_notification_signal_due (signal_pending, signal_next_attempt_at),
    CONSTRAINT fk_notification_inbox_recipient FOREIGN KEY (recipient)
        REFERENCES security_principals(subject_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sequence BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    type VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_event_recipient (event_id, recipient),
    UNIQUE KEY uk_notification_recipient_sequence (recipient, sequence),
    INDEX idx_notification_unread (recipient, read_at, sequence),
    INDEX idx_notification_retention (created_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient)
        REFERENCES notification_inboxes(recipient) ON DELETE CASCADE,
    CONSTRAINT fk_notification_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
