package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** 不包含正文或凭证的权限与业务审计事件。 */
@Data
public class AuditEvent {
    private Long id;
    private String eventId;
    private Long workspaceId;
    private String actorSubject;
    private PrincipalType actorType;
    private String action;
    private String resourceType;
    private Long resourceId;
    private AuditResult result;
    private LocalDateTime occurredAt;
    private String traceId;
}
