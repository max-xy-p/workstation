CREATE TABLE IF NOT EXISTS mail_messages (
    id VARCHAR(36) PRIMARY KEY,
    caller VARCHAR(32) NOT NULL,
    request_key VARBINARY(128) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload MEDIUMTEXT,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    lease_token VARCHAR(36),
    lease_until TIMESTAMP(6),
    provider_id VARCHAR(128),
    error_code VARCHAR(64),
    delivery_at TIMESTAMP(6),
    UNIQUE (caller, request_key),
    INDEX mail_due (status, next_attempt_at)
);
CREATE TABLE IF NOT EXISTS mail_dispatch_clock (
    id INT PRIMARY KEY,
    next_at TIMESTAMP(6) NOT NULL
);
INSERT INTO mail_dispatch_clock (id, next_at)
SELECT 1, '2000-01-01 00:00:00' WHERE NOT EXISTS (SELECT 1 FROM mail_dispatch_clock WHERE id = 1);
CREATE TABLE IF NOT EXISTS mail_delivery_events (
    event_id VARBINARY(128) PRIMARY KEY,
    provider_id VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    INDEX mail_delivery_lookup (provider_id, occurred_at)
);
