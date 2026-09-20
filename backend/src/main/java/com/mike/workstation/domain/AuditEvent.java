package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
/** 不包含正文或凭证的权限与业务审计事件。 */
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
