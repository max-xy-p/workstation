USE workstation;

-- One-time migration for databases created before note persistence receipts.
ALTER TABLE notes
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER project_id;
