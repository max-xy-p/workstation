package com.mike.workstation.service;

import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import com.mike.workstation.domain.AuditResult;
import com.mike.workstation.security.CurrentPrincipalResolver;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 业务代码写审计事件的统一入口。
 *
 * <p>这里只组装事件，不直接连接 RabbitMQ。成功事件先进入本地 Outbox，以保证业务提交与事件产生
 * 原子一致；拒绝事件采用尽力记录策略，防止辅助审计故障改变授权结果。</p>
 */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditOutboxWriter outboxWriter;
    private final CurrentPrincipalResolver principalResolver;

    public AuditService(AuditOutboxWriter outboxWriter, CurrentPrincipalResolver principalResolver) {
        this.outboxWriter = outboxWriter;
        this.principalResolver = principalResolver;
    }

    /**
     * 记录成功动作。调用方必须是带事务的写 Service，Outbox 写失败会让业务一起回滚。
     */
    public void success(Long workspaceId, String action, String resourceType, Long resourceId) {
        outboxWriter.writeRequired(create(workspaceId, action, resourceType, resourceId, AuditResult.SUCCESS));
    }

    /**
     * 尽力记录权限拒绝。任何审计异常都只写内部日志，原调用仍继续返回应有的 403 或 404。
     */
    public void denied(Long workspaceId, String action, String resourceType, Long resourceId) {
        try {
            outboxWriter.writeNewTransaction(create(
                    workspaceId, action, resourceType, resourceId, AuditResult.DENIED));
        } catch (RuntimeException exception) {
            // 审计是辅助能力；失败不能把预期的 403/404 改写成 500。
            log.error("权限拒绝审计事件写入 Outbox 失败，workspaceId={}，action={}",
                    workspaceId, action, exception);
        }
    }

    private AuditOutbox create(
            Long workspaceId, String action, String resourceType, Long resourceId, AuditResult result
    ) {
        // 在业务线程中固化操作者快照；异步消费线程没有原请求的 SecurityContext。
        var principal = principalResolver.require();
        LocalDateTime now = LocalDateTime.now();
        AuditOutbox event = new AuditOutbox();
        // 同一 eventId 会一路传到最终表，用于抵御 At-least-once 投递产生的重复消息。
        event.setEventId(UUID.randomUUID().toString());
        event.setWorkspaceId(workspaceId);
        event.setActorSubject(principal.subjectId());
        event.setActorType(principal.principalType());
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setResult(result);
        event.setOccurredAt(now);
        event.setStatus(AuditOutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        return event;
    }
}
