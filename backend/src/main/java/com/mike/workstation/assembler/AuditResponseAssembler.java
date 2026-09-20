package com.mike.workstation.assembler;

import com.mike.workstation.domain.AuditEvent;
import com.mike.workstation.dto.AuditEventResponse;

/** 审计事件到脱敏 API Response 的转换。 */
public final class AuditResponseAssembler {
    private AuditResponseAssembler() {
    }

    public static AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getActorSubject(),
                event.getActorType(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getResult(),
                ApiTime.toOffset(event.getOccurredAt()),
                event.getTraceId()
        );
    }
}
