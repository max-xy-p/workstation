package com.mike.workstation.dto;

import com.mike.workstation.domain.AuditResult;
import com.mike.workstation.domain.PrincipalType;
import java.time.OffsetDateTime;

/** 对外暴露的脱敏审计事件。 */
public record AuditEventResponse(
        Long id,
        String actorSubject,
        PrincipalType actorType,
        String action,
        String resourceType,
        Long resourceId,
        AuditResult result,
        OffsetDateTime occurredAt,
        String traceId
) {
}
