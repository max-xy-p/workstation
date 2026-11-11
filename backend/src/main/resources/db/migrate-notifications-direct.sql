-- 暂停旧应用写入并排空旧通知生成任务后执行；保留旧表，不删除通知历史。
USE workstation;

DROP PROCEDURE IF EXISTS migrate_workstation_notifications_direct;
DELIMITER //
CREATE PROCEDURE migrate_workstation_notifications_direct()
BEGIN
    DECLARE legacy_inbox BOOLEAN DEFAULT FALSE;
    DECLARE pending_count BIGINT DEFAULT 0;
    DECLARE inbox_table VARCHAR(64) DEFAULT 'notification_inboxes';
    IF EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'notification_outbox') THEN
        SELECT COUNT(*) INTO pending_count FROM notification_outbox
            WHERE state IN ('PENDING', 'PUBLISHED');
        IF pending_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '旧通知生成任务尚未排空，请先恢复旧消费者完成处理';
        END IF;
        INSERT IGNORE INTO notification_events (event_id, workspace_id, fingerprint, created_at)
            SELECT event_id, workspace_id, fingerprint, created_at FROM notification_outbox;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = inbox_table
            AND column_name = 'signal_pending') THEN
        ALTER TABLE notification_inboxes ADD COLUMN signal_pending BOOLEAN NOT NULL DEFAULT FALSE;
        SET legacy_inbox = TRUE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = inbox_table
            AND column_name = 'signal_next_attempt_at') THEN
        ALTER TABLE notification_inboxes ADD COLUMN signal_next_attempt_at DATETIME(6)
            NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = inbox_table
            AND column_name = 'signal_lease_token') THEN
        ALTER TABLE notification_inboxes ADD COLUMN signal_lease_token
            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = inbox_table
            AND index_name = 'idx_notification_signal_due') THEN
        ALTER TABLE notification_inboxes ADD INDEX idx_notification_signal_due
            (signal_pending, signal_next_attempt_at);
    END IF;
    IF legacy_inbox THEN
        UPDATE notification_inboxes SET signal_pending = TRUE WHERE revision > 0;
    END IF;
END//
DELIMITER ;
CALL migrate_workstation_notifications_direct();
DROP PROCEDURE migrate_workstation_notifications_direct;
