USE workstation;

-- 在已完成 Workspace 迁移的数据库上执行；执行前备份。
-- 新安装使用 schema.sql，无需单独执行本脚本。
DROP PROCEDURE IF EXISTS migrate_note_trash;
DELIMITER $$
CREATE PROCEDURE migrate_note_trash()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notes' AND column_name = 'deleted_at'
    ) THEN
        ALTER TABLE notes ADD COLUMN deleted_at DATETIME NULL AFTER version;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notes' AND column_name = 'deleted_by_subject'
    ) THEN
        ALTER TABLE notes ADD COLUMN deleted_by_subject
            VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER deleted_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'notes'
          AND index_name = 'idx_notes_workspace_deleted'
    ) THEN
        ALTER TABLE notes ADD INDEX idx_notes_workspace_deleted (workspace_id, deleted_at);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'notes'
          AND constraint_name = 'fk_notes_deleted_by' AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE notes ADD CONSTRAINT fk_notes_deleted_by
            FOREIGN KEY (deleted_by_subject) REFERENCES security_principals (subject_id);
    END IF;
END$$
DELIMITER ;

CALL migrate_note_trash();
DROP PROCEDURE migrate_note_trash;
