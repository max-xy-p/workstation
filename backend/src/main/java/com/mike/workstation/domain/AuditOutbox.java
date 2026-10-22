package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务事务中产生、等待可靠投递到 RabbitMQ 的审计事件。
 *
 * <p>它不是最终审计记录，而是业务数据库与消息队列之间的“交接单”。业务数据和该记录 一起提交后，即使 RabbitMQ 暂时不可用，后台任务仍可在稍后继续投递。
 */
@Data
public class AuditOutbox {
    private Long id;

    /** 贯穿 Outbox、RabbitMQ 和最终审计表的幂等标识。 */
    private String eventId;

    private Long workspaceId;
    private String actorSubject;
    private PrincipalType actorType;
    private String action;
    private String resourceType;
    private Long resourceId;
    private AuditResult result;

    /** 业务动作实际发生的时间，不是消息最终被消费的时间。 */
    private LocalDateTime occurredAt;

    private String traceId;

    /** PENDING -> SENDING -> PUBLISHED；超过最大尝试次数后为 FAILED。 */
    private AuditOutboxStatus status;

    /** 已经领取并尝试发布的次数。 */
    private int attempts;

    private LocalDateTime nextAttemptAt;

    /** 当前 Relay 的租约标识，用于防止其他实例同时更新这条记录。 */
    private String lockedBy;

    /** Relay 异常退出后，超过该时间的 SENDING 记录可以被重新领取。 */
    private LocalDateTime lockedUntil;

    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
