package com.mike.workstation.mail;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MailWorker {
    private static final Logger LOG = LoggerFactory.getLogger(MailWorker.class);
    private final MailTaskRepository repository;
    private final PayloadCipher cipher;
    private final ObjectMapper json;
    private final ResendClient resend;
    private final Clock clock;
    private final MailProperties properties;

    public MailWorker(MailTaskRepository repository, PayloadCipher cipher, ObjectMapper json,
                      ResendClient resend, Clock clock, MailProperties properties) {
        this.repository = repository;
        this.cipher = cipher;
        this.json = json;
        this.resend = resend;
        this.clock = clock;
        this.properties = properties;
    }

    public MailDeliveryResult dispatch(MailDispatch dispatch) {
        MailTaskRepository.Claim claim = repository.claim(dispatch, clock.instant());
        if (claim.task() == null) {
            return new MailDeliveryResult(claim.delaySeconds(), claim.failed());
        }
        MailTask task = claim.task();
        MailRequest request;
        try {
            request = json.readValue(cipher.decrypt(task.id(), task.payload()), MailRequest.class);
        } catch (RuntimeException error) {
            repository.finish(task, "FAILED", null, "PAYLOAD_UNREADABLE");
            return new MailDeliveryResult(0, true);
        }
        if (!task.expiresAt().isAfter(clock.instant())) {
            repository.finish(task, "EXPIRED", null, null);
            return MailDeliveryResult.done();
        }
        ResendClient.Result result = resend.send(task.id(), request);
        if (result.providerId() != null) {
            repository.finish(task, "SUBMITTED", result.providerId(), null);
            return MailDeliveryResult.done();
        }
        // 领取时已限制最多八次，指数退避无需重复截断。
        Instant next = clock.instant().plusSeconds(Math.max(result.retryAfterSeconds(), 5L << task.attempts()));
        if (result.retryable() && task.attempts() < 8 && next.isBefore(task.expiresAt())) {
            // 重试状态与新版本 Outbox 同事务提交后，消费者才允许确认当前消息。
            repository.retry(task, next, result.errorCode(), clock.instant());
            return MailDeliveryResult.done();
        } else {
            repository.finish(task, "FAILED", null, result.errorCode());
            return new MailDeliveryResult(0, true);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void purge() {
        if (properties.workerEnabled()) {
            try {
                repository.purge(clock.instant());
            } catch (RuntimeException error) {
                LOG.warn("邮件保留期清理暂时失败，稍后重试");
            }
        }
    }
}
