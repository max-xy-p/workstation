USE workstation;

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL,
    repository_url VARCHAR(500) NULL,
    tech_stack VARCHAR(500) NULL,
    run_guide TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_projects_status (status),
    INDEX idx_projects_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS note_folders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    parent_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_note_folders_parent_id (parent_id),
    UNIQUE KEY uk_note_folders_parent_name (parent_id, name),
    CONSTRAINT fk_note_folders_parent
        FOREIGN KEY (parent_id) REFERENCES note_folders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tags_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    project_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    due_date DATE NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_tasks_project_id (project_id),
    INDEX idx_tasks_status (status),
    INDEX idx_tasks_priority (priority),
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    folder_id BIGINT NULL,
    project_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notes_folder_id (folder_id),
    INDEX idx_notes_project_id (project_id),
    INDEX idx_notes_updated_at (updated_at),
    CONSTRAINT fk_notes_folder
        FOREIGN KEY (folder_id) REFERENCES note_folders (id),
    CONSTRAINT fk_notes_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS note_tag (
    note_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (note_id, tag_id),
    INDEX idx_note_tag_tag_id (tag_id),
    CONSTRAINT fk_note_tag_note
        FOREIGN KEY (note_id) REFERENCES notes (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_note_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
