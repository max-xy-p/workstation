package com.mike.workstation.service;

import com.mike.workstation.config.AuditOutboxProperties;
import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import com.mike.workstation.mapper.AuditOutboxMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在独立短事务中确认发布或安排下一次 Outbox 重试。 */
@Service
public class AuditOutboxStateService {
    private static final int LAST_ERROR_LIMIT = 1000;
    private static final int MAX_BACKOFF_EXPONENT = 10;

    private final AuditOutboxMapper outboxMapper;
    private final AuditOutboxProperties properties;

    public AuditOutboxStateService(AuditOutboxMapper outboxMapper, AuditOutboxProperties properties) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
    }

    /** RabbitMQ 已确认接收消息后，将当前租约下的记录标记为发布完成。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(AuditOutbox event) {
        int updated = outboxMapper.markPublished(event.getId(), event.getLockedBy(), LocalDateTime.now());
        requireUpdated(updated, event);
    }

    /**
     * 发布失败后释放租约。未耗尽次数时回到 PENDING，耗尽后转为 FAILED 等待人工处理。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(AuditOutbox event, Throwable failure) {
        boolean exhausted = event.getAttempts() >= properties.maxAttempts();
        AuditOutboxStatus status = exhausted ? AuditOutboxStatus.FAILED : AuditOutboxStatus.PENDING;
        LocalDateTime nextAttemptAt = exhausted
                ? LocalDateTime.now()
                : LocalDateTime.now().plus(retryDelay(event.getAttempts()));
        int updated = outboxMapper.markFailure(
                event.getId(),
                event.getLockedBy(),
                status,
                nextAttemptAt,
                errorMessage(failure)
        );
        requireUpdated(updated, event);
    }

    private Duration retryDelay(int attempts) {
        // 退避间隔按 1、2、4、8... 倍增长，并限制指数，避免溢出或产生不可控的超长间隔。
        int exponent = Math.min(Math.max(attempts - 1, 0), MAX_BACKOFF_EXPONENT);
        return properties.retryBaseDelay().multipliedBy(1L << exponent);
    }

    private String errorMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() <= LAST_ERROR_LIMIT ? message : message.substring(0, LAST_ERROR_LIMIT);
    }

    private void requireUpdated(int updated, AuditOutbox event) {
        // 更新数量为 0 通常意味着租约已经过期或被其他 Relay 接管，不能继续假装状态更新成功。
        if (updated != 1) {
            throw new IllegalStateException("审计 Outbox 状态更新失败，eventId=" + event.getEventId());
        }
    }
}
