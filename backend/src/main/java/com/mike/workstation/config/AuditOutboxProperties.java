package com.mike.workstation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workstation.audit.outbox")
/** 审计 Outbox 领取、发布确认和退避重试参数。 */
public record AuditOutboxProperties(
        int batchSize,
        Duration lockDuration,
        Duration publishConfirmTimeout,
        int maxAttempts,
        Duration retryBaseDelay
) {
    public AuditOutboxProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("审计 Outbox 批量大小必须大于 0");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("审计 Outbox 租约时间必须大于 0");
        }
        if (publishConfirmTimeout == null || publishConfirmTimeout.isNegative() || publishConfirmTimeout.isZero()) {
            throw new IllegalArgumentException("审计消息发布确认超时时间必须大于 0");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("审计 Outbox 最大尝试次数必须大于 0");
        }
        if (retryBaseDelay == null || retryBaseDelay.isNegative() || retryBaseDelay.isZero()) {
            throw new IllegalArgumentException("审计 Outbox 重试基础间隔必须大于 0");
        }
    }
}
