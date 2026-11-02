package com.mike.workstation.mail;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 只补偿消息发布；邮件发送、延期和消费由 RabbitMQ 驱动。 */
@Component
public class MailOutboxRelay {
    private static final Logger LOG = LoggerFactory.getLogger(MailOutboxRelay.class);
    private final MailOutboxRepository repository;
    private final MailMessagePublisher publisher;
    private final MailProperties properties;
    private final Clock clock;

    public MailOutboxRelay(MailOutboxRepository repository, MailMessagePublisher publisher,
                           MailProperties properties, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        if (!properties.workerEnabled()) {
            return;
        }
        try {
            for (int count = 0; count < 20; count++) {
                if (!relayOne()) {
                    break;
                }
            }
        } catch (RuntimeException error) {
            LOG.warn("邮件 Outbox 暂时无法处理，保留记录等待恢复");
        }
    }

    boolean relayOne() {
        MailOutboxRepository.Entry entry = repository.claim(clock.instant());
        if (entry == null) {
            return false;
        }
        try {
            Instant now = clock.instant();
            boolean expired = !entry.expiresAt().isAfter(now);
            if (!expired) {
                long remaining = Math.max(0, entry.createdAt().plusSeconds(entry.delaySeconds()).getEpochSecond() - now.getEpochSecond());
                publisher.publish(entry.dispatch(), remaining);
            }
            repository.published(entry, expired);
            return true;
        } catch (RuntimeException error) {
            repository.failed(entry, clock.instant());
            LOG.warn("邮件消息尚未获得发布确认，将稍后重试");
            return false;
        }
    }
}
