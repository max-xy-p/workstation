USE workstation;

-- One-time migration for databases created before hierarchical note folders.
CREATE TABLE note_folders (
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

INSERT INTO note_folders (id, name, parent_id, created_at, updated_at)
SELECT id, name, NULL, created_at, updated_at
FROM categories;

ALTER TABLE notes ADD COLUMN folder_id BIGINT NULL AFTER content;
UPDATE notes SET folder_id = category_id;

ALTER TABLE notes
    DROP FOREIGN KEY fk_notes_category,
    DROP INDEX idx_notes_category_id,
    DROP COLUMN category_id,
    ADD INDEX idx_notes_folder_id (folder_id),
    ADD CONSTRAINT fk_notes_folder
        FOREIGN KEY (folder_id) REFERENCES note_folders (id);

DROP TABLE categories;
