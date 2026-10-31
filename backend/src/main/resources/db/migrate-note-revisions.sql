USE workstation;

-- 在维护窗口暂停写入并备份后执行；新安装在基础表初始化后执行同一文件。
CREATE TABLE IF NOT EXISTS note_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    note_id BIGINT NOT NULL,
    note_version BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    actor_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME NOT NULL,
    source VARCHAR(16) NOT NULL,
    restored_from_revision_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_revision_version (workspace_id, note_id, note_version),
    CONSTRAINT fk_note_revision_note
        FOREIGN KEY (workspace_id, note_id) REFERENCES notes (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_note_revision_version CHECK (note_version > 0),
    CONSTRAINT ck_note_revision_source CHECK (source IN ('CREATE', 'IMPORT', 'UPDATE', 'RESTORE', 'BASELINE')),
    CONSTRAINT ck_note_revision_restore CHECK (
        (source = 'RESTORE') = (restored_from_revision_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 只记录当前可验证的内容，不推测旧作者，不改变 notes.version；重复执行不重复补齐同一版本。
INSERT INTO note_revisions (workspace_id, note_id, note_version, title, content, actor_subject, created_at, source)
SELECT n.workspace_id, n.id, n.version, n.title, n.content, NULL, NOW(), 'BASELINE'
FROM notes n
LEFT JOIN note_revisions r
    ON r.workspace_id = n.workspace_id AND r.note_id = n.id AND r.note_version = n.version
WHERE r.id IS NULL;
