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

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        if (!properties.workerEnabled()) {
            return;
        }
        try {
            dispatchOne();
        } catch (RuntimeException error) {
            // 仅记录固定错误码；租约到期后恢复，不输出可能带敏感参数的 SQL/HTTP 异常。
            LOG.warn("邮件任务处理暂时失败，将在租约到期后恢复");
        }
    }

    void dispatchOne() {
        MailTask task = repository.claim(clock.instant());
        if (task == null) {
            return;
        }
        MailRequest request;
        try {
            request = json.readValue(cipher.decrypt(task.id(), task.payload()), MailRequest.class);
        } catch (RuntimeException error) {
            repository.finish(task, "FAILED", null, "PAYLOAD_UNREADABLE");
            return;
        }
        if (!task.expiresAt().isAfter(clock.instant())) {
            repository.finish(task, "EXPIRED", null, null);
            return;
        }
        ResendClient.Result result = resend.send(task.id(), request);
        if (result.providerId() != null) {
            repository.finish(task, "SUBMITTED", result.providerId(), null);
            return;
        }
        Instant next = clock.instant().plusSeconds(Math.max(result.retryAfterSeconds(),
                Math.min(3600, 5L << Math.min(task.attempts(), 9))));
        if (result.retryable() && task.attempts() < 8 && next.isBefore(task.expiresAt())) {
            repository.retry(task, next, result.errorCode());
        } else {
            repository.finish(task, "FAILED", null, result.errorCode());
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
