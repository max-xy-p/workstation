package com.mike.workstation.messaging;

import com.mike.workstation.domain.AuditEvent;
import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditResult;
import com.mike.workstation.domain.PrincipalType;

import java.time.LocalDateTime;

/**
 * RabbitMQ 中传递的不可变审计事件快照。
 *
 * <p>消息必须包含最终落库所需的全部信息，消费者不能再读取原请求的 SecurityContext， 也不应重新查询可能已经变化的业务对象来拼装历史事件。
 */
public record AuditMessage(
        String eventId,
        Long workspaceId,
        String actorSubject,
        PrincipalType actorType,
        String action,
        String resourceType,
        Long resourceId,
        AuditResult result,
        LocalDateTime occurredAt,
        String traceId) {
    public static AuditMessage from(AuditOutbox event) {
        return new AuditMessage(
                event.getEventId(),
                event.getWorkspaceId(),
                event.getActorSubject(),
                event.getActorType(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getResult(),
                event.getOccurredAt(),
                event.getTraceId());
    }

    public AuditEvent toEvent() {
        // 只转换为最终表 Entity；消息消费阶段不再执行权限判断或业务规则。
        AuditEvent event = new AuditEvent();
        event.setEventId(eventId);
        event.setWorkspaceId(workspaceId);
        event.setActorSubject(actorSubject);
        event.setActorType(actorType);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setResult(result);
        event.setOccurredAt(occurredAt);
        event.setTraceId(traceId);
        return event;
    }
}
