USE workstation;

-- 已有数据库的一次性 Workspace 升级脚本。
-- 执行前必须备份数据库，并把下面两个值替换成当前 Keycloak 用户的真实信息。
SET @owner_subject = 'replace-with-keycloak-sub';
SET @owner_username = 'replace-with-keycloak-username';
SET @now = NOW();

CREATE TABLE security_principals (
    subject_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    principal_type VARCHAR(32) NOT NULL,
    username VARCHAR(255) NULL,
    display_name VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (subject_id),
    INDEX idx_security_principals_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspaces (
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

CREATE TABLE workspace_members (
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

CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
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
    INDEX idx_audit_workspace_time (workspace_id, occurred_at, id),
    INDEX idx_audit_workspace_actor (workspace_id, actor_subject),
    INDEX idx_audit_workspace_action (workspace_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO security_principals
    (subject_id, principal_type, username, display_name, created_at, updated_at)
VALUES
    (@owner_subject, 'USER', @owner_username, @owner_username, @now, @now);

INSERT INTO workspaces
    (name, type, personal_owner_subject, created_by_subject, created_at, updated_at)
VALUES
    (CONCAT(@owner_username, '的知识库'), 'PERSONAL', @owner_subject, @owner_subject, @now, @now);

SET @workspace_id = LAST_INSERT_ID();

INSERT INTO workspace_members (workspace_id, principal_subject, role, joined_at)
VALUES (@workspace_id, @owner_subject, 'OWNER', @now);

ALTER TABLE projects ADD COLUMN workspace_id BIGINT NULL AFTER id;
ALTER TABLE note_folders ADD COLUMN workspace_id BIGINT NULL AFTER id;
ALTER TABLE tags ADD COLUMN workspace_id BIGINT NULL AFTER id;
ALTER TABLE tasks ADD COLUMN workspace_id BIGINT NULL AFTER id;
ALTER TABLE notes ADD COLUMN workspace_id BIGINT NULL AFTER id;
ALTER TABLE note_tag ADD COLUMN workspace_id BIGINT NULL FIRST;

UPDATE projects SET workspace_id = @workspace_id;
UPDATE note_folders SET workspace_id = @workspace_id;
UPDATE tags SET workspace_id = @workspace_id;
UPDATE tasks SET workspace_id = @workspace_id;
UPDATE notes SET workspace_id = @workspace_id;
UPDATE note_tag SET workspace_id = @workspace_id;

ALTER TABLE note_folders DROP FOREIGN KEY fk_note_folders_parent;
ALTER TABLE tasks DROP FOREIGN KEY fk_tasks_project;
ALTER TABLE notes DROP FOREIGN KEY fk_notes_folder, DROP FOREIGN KEY fk_notes_project;
ALTER TABLE note_tag DROP FOREIGN KEY fk_note_tag_note, DROP FOREIGN KEY fk_note_tag_tag;

ALTER TABLE projects MODIFY workspace_id BIGINT NOT NULL;
ALTER TABLE note_folders MODIFY workspace_id BIGINT NOT NULL;
ALTER TABLE tags MODIFY workspace_id BIGINT NOT NULL;
ALTER TABLE tasks MODIFY workspace_id BIGINT NOT NULL;
ALTER TABLE notes MODIFY workspace_id BIGINT NOT NULL;
ALTER TABLE note_tag MODIFY workspace_id BIGINT NOT NULL;

ALTER TABLE projects
    ADD UNIQUE KEY uk_projects_workspace_id_id (workspace_id, id),
    ADD INDEX idx_projects_workspace_id (workspace_id),
    ADD CONSTRAINT fk_projects_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id);

ALTER TABLE note_folders
    DROP INDEX uk_note_folders_parent_name,
    ADD UNIQUE KEY uk_note_folders_workspace_id_id (workspace_id, id),
    ADD INDEX idx_note_folders_workspace_parent (workspace_id, parent_id),
    ADD UNIQUE KEY uk_note_folders_workspace_parent_name (workspace_id, parent_id, name),
    ADD CONSTRAINT fk_note_folders_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    ADD CONSTRAINT fk_note_folders_parent
        FOREIGN KEY (workspace_id, parent_id) REFERENCES note_folders (workspace_id, id);

ALTER TABLE tags
    DROP INDEX uk_tags_name,
    ADD UNIQUE KEY uk_tags_workspace_id_id (workspace_id, id),
    ADD UNIQUE KEY uk_tags_workspace_name (workspace_id, name),
    ADD CONSTRAINT fk_tags_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id);

ALTER TABLE tasks
    ADD INDEX idx_tasks_workspace_id (workspace_id),
    ADD INDEX idx_tasks_workspace_project (workspace_id, project_id),
    ADD CONSTRAINT fk_tasks_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    ADD CONSTRAINT fk_tasks_project
        FOREIGN KEY (workspace_id, project_id) REFERENCES projects (workspace_id, id);

ALTER TABLE notes
    ADD UNIQUE KEY uk_notes_workspace_id_id (workspace_id, id),
    ADD INDEX idx_notes_workspace_id (workspace_id),
    ADD INDEX idx_notes_workspace_folder (workspace_id, folder_id),
    ADD INDEX idx_notes_workspace_project (workspace_id, project_id),
    ADD CONSTRAINT fk_notes_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    ADD CONSTRAINT fk_notes_folder
        FOREIGN KEY (workspace_id, folder_id) REFERENCES note_folders (workspace_id, id),
    ADD CONSTRAINT fk_notes_project
        FOREIGN KEY (workspace_id, project_id) REFERENCES projects (workspace_id, id);

ALTER TABLE note_tag
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (workspace_id, note_id, tag_id),
    ADD INDEX idx_note_tag_workspace_tag (workspace_id, tag_id),
    ADD CONSTRAINT fk_note_tag_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    ADD CONSTRAINT fk_note_tag_note
        FOREIGN KEY (workspace_id, note_id) REFERENCES notes (workspace_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_note_tag_tag
        FOREIGN KEY (workspace_id, tag_id) REFERENCES tags (workspace_id, id);
