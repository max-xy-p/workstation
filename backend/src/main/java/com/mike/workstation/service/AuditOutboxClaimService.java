package com.mike.workstation.service;

import com.mike.workstation.config.AuditOutboxProperties;
import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import com.mike.workstation.mapper.AuditOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以短事务和租约领取待发布事件，不在等待 RabbitMQ 时持有行锁。 */
@Service
public class AuditOutboxClaimService {
    private final AuditOutboxMapper outboxMapper;
    private final AuditOutboxProperties properties;

    public AuditOutboxClaimService(AuditOutboxMapper outboxMapper, AuditOutboxProperties properties) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
    }

    /**
     * 领取一批可投递事件，并在同一短事务中把它们标记为 SENDING。
     *
     * <p>查询使用 {@code FOR UPDATE SKIP LOCKED}，多个 Backend 实例可以并行领取不同记录。
     * 返回后事务已经结束，后续等待 RabbitMQ Confirm 时不会长期占用数据库行锁。</p>
     */
    @Transactional
    public List<AuditOutbox> claimBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<AuditOutbox> events = outboxMapper.findClaimable(now, properties.batchSize());
        if (events.isEmpty()) {
            return List.of();
        }

        // 每批使用不同租约标识，状态更新时必须同时匹配 id 和 lockedBy，避免旧执行器误更新新租约。
        String lockOwner = UUID.randomUUID().toString();
        LocalDateTime lockedUntil = now.plus(properties.lockDuration());
        List<Long> ids = events.stream().map(AuditOutbox::getId).toList();
        int updated = outboxMapper.markSending(ids, lockOwner, lockedUntil);
        if (updated != events.size()) {
            throw new IllegalStateException("审计 Outbox 领取数量与更新数量不一致");
        }
        // Mapper 更新的是数据库，这里同步内存对象，后续发布失败时需要用新 attempts 计算退避时间。
        events.forEach(event -> {
            event.setStatus(AuditOutboxStatus.SENDING);
            event.setAttempts(event.getAttempts() + 1);
            event.setLockedBy(lockOwner);
            event.setLockedUntil(lockedUntil);
        });
        return events;
    }
}
