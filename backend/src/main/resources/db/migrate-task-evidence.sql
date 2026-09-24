USE workstation;

-- 在已经完成 Workspace 迁移的数据库上执行；执行前备份。
-- 新安装使用 schema.sql，无需单独执行本脚本。
DROP PROCEDURE IF EXISTS migrate_task_evidence;
DELIMITER $$
CREATE PROCEDURE migrate_task_evidence()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'tasks' AND column_name = 'evidence_note_id'
    ) THEN
        ALTER TABLE tasks ADD COLUMN evidence_note_id BIGINT NULL AFTER project_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'tasks'
          AND index_name = 'idx_tasks_workspace_evidence'
    ) THEN
        ALTER TABLE tasks ADD INDEX idx_tasks_workspace_evidence (workspace_id, evidence_note_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'tasks'
          AND constraint_name = 'fk_tasks_evidence_note' AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE tasks ADD CONSTRAINT fk_tasks_evidence_note
            FOREIGN KEY (workspace_id, evidence_note_id) REFERENCES notes (workspace_id, id);
    END IF;
END$$
DELIMITER ;

CALL migrate_task_evidence();
DROP PROCEDURE migrate_task_evidence;
