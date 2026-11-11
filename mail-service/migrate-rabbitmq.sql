-- 已有邮件库升级前停止所有旧邮件工作进程并备份；在选定的邮件数据库内执行。
SET @mail_version_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'mail_messages' AND column_name = 'dispatch_version'
);
SET @mail_ddl = IF(@mail_version_column = 0,
    'ALTER TABLE mail_messages ADD COLUMN dispatch_version BIGINT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE mail_statement FROM @mail_ddl;
EXECUTE mail_statement;
DEALLOCATE PREPARE mail_statement;

CREATE TABLE IF NOT EXISTS mail_outbox (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    dispatch_version BIGINT NOT NULL,
    delay_seconds BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    lease_token VARCHAR(36),
    lease_until TIMESTAMP(6),
    UNIQUE (task_id, dispatch_version),
    INDEX mail_outbox_pending (state, next_attempt_at)
);

START TRANSACTION;
-- 仅接管尚无 Outbox 的旧任务，重复迁移不会改变新版消费者的租约或发布状态。
UPDATE mail_messages m SET m.status = 'QUEUED', m.lease_token = NULL, m.lease_until = NULL
WHERE m.status = 'SENDING' AND NOT EXISTS (
    SELECT 1 FROM mail_outbox o WHERE o.task_id = m.id AND o.dispatch_version = m.dispatch_version
);
INSERT INTO mail_outbox (id, task_id, dispatch_version, delay_seconds, created_at, expires_at, next_attempt_at)
SELECT UUID(), m.id, m.dispatch_version,
       GREATEST(0, TIMESTAMPDIFF(SECOND, CURRENT_TIMESTAMP(6), m.next_attempt_at)),
       CURRENT_TIMESTAMP(6), m.expires_at, CURRENT_TIMESTAMP(6)
FROM mail_messages m
LEFT JOIN mail_outbox o ON o.task_id = m.id AND o.dispatch_version = m.dispatch_version
WHERE m.status = 'QUEUED' AND m.payload IS NOT NULL AND m.expires_at > CURRENT_TIMESTAMP(6)
  AND o.id IS NULL;
COMMIT;
