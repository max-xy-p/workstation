package com.mike.workstation.domain;

/** 审计 Outbox 从待投递到发布完成的状态。 */
public enum AuditOutboxStatus {
    PENDING,
    SENDING,
    PUBLISHED,
    FAILED
}
