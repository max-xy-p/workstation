package com.mike.workstation.service;

import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.messaging.AuditMessagePublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 领取一批 Outbox，逐条发布并独立记录结果，避免单条失败重发整批。 */
@Service
public class AuditOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(AuditOutboxRelay.class);

    private final AuditOutboxClaimService claimService;
    private final AuditMessagePublisher publisher;
    private final AuditOutboxStateService stateService;

    public AuditOutboxRelay(
            AuditOutboxClaimService claimService,
            AuditMessagePublisher publisher,
            AuditOutboxStateService stateService) {
        this.claimService = claimService;
        this.publisher = publisher;
        this.stateService = stateService;
    }

    /**
     * 执行一次搬运。
     *
     * <p>每条事件独立发布和落状态。一条失败不会中断循环，否则前面已经由 Broker 确认的消息 可能因为整批重试而被重复发送。即便出现重复发送，消费者仍会使用 eventId 幂等落库。
     */
    public AuditRelayResult relayBatch() {
        List<AuditOutbox> events = claimService.claimBatch();
        int published = 0;
        int failed = 0;
        for (AuditOutbox event : events) {
            try {
                // 只有收到 Broker Confirm 后，才允许把 Outbox 标记为 PUBLISHED。
                publisher.publish(event);
                stateService.markPublished(event);
                published++;
            } catch (Exception exception) {
                failed++;
                log.warn(
                        "审计 Outbox 发布失败，eventId={}，attempts={}",
                        event.getEventId(),
                        event.getAttempts(),
                        exception);
                try {
                    stateService.markFailure(event, exception);
                } catch (RuntimeException stateException) {
                    // 状态库也不可用时保留 SENDING；租约到期后它会重新成为可领取记录。
                    log.error("审计 Outbox 失败状态更新异常，eventId={}", event.getEventId(), stateException);
                }
            }
        }
        return new AuditRelayResult(events.size(), published, failed);
    }
}
