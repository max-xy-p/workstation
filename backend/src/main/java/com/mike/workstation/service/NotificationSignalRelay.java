package com.mike.workstation.service;

import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.mapper.NotificationSignalMapper;
import com.mike.workstation.mapper.NotificationEventMapper;
import com.mike.workstation.mapper.NotificationMapper;
import com.mike.workstation.messaging.NotificationMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** 读取已提交的待发布版本，MQ 故障或进程退出后通过租约恢复广播。 */
@Component
public class NotificationSignalRelay {
    private static final Logger log = LoggerFactory.getLogger(NotificationSignalRelay.class);
    private final NotificationProperties properties;
    private final NotificationSignalMapper signals;
    private final NotificationMessagePublisher publisher;
    private final NotificationMapper notifications;
    private final NotificationEventMapper events;

    public NotificationSignalRelay(NotificationProperties properties, NotificationSignalMapper signals,
            NotificationMessagePublisher publisher, NotificationMapper notifications, NotificationEventMapper events) {
        this.properties = properties;
        this.signals = signals;
        this.publisher = publisher;
        this.notifications = notifications;
        this.events = events;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        if (properties.enabled()) {
            LocalDateTime now = now();
            for (String recipient : signals.due(now)) {
                relayOne(recipient, now);
            }
        }
    }

    public void relayOne(String recipient, LocalDateTime now) {
        String lease = UUID.randomUUID().toString();
        if (signals.claim(recipient, lease, now, now.plusSeconds(30)) == 0) {
            return;
        }
        var refresh = signals.snapshot(recipient);
        if (refresh == null) {
            return;
        }
        try {
            publisher.publish(refresh);
            signals.published(recipient, lease, refresh.revision(), now());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("通知刷新发布中断，将在租约到期后恢复");
        } catch (Exception exception) {
            log.warn("通知刷新发布暂不可用，将在租约到期后恢复");
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void maintenance() {
        if (properties.enabled()) {
            LocalDateTime before = now().minusDays(properties.retentionDays());
            notifications.purge(before);
            events.purge(before);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }
}
