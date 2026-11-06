package com.mike.workstation.service;

import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.exception.NotificationStreamLimitException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;

/** 用户通知复用有界 SSE 传输，每个用户对应独立主题。 */
@Component
public class NotificationStreamHub extends RefreshStreamHub {
    public NotificationStreamHub(NotificationProperties properties) {
        super(properties.enabled(), properties.maxConnections(), properties.connectionsPerUser(),
                NotificationStreamLimitException::new);
    }

    public SseEmitter open(String subject, Instant expiresAt) {
        return super.open(subject, subject, expiresAt);
    }
}
