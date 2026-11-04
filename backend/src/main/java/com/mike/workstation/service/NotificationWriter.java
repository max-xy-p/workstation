package com.mike.workstation.service;

import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.domain.Notification;
import com.mike.workstation.domain.NotificationEvent;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.mapper.NotificationEventMapper;
import com.mike.workstation.mapper.NotificationMapper;
import com.mike.workstation.mapper.WorkspaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** 在调用方业务事务内直接保存通知；MQ 仅负责提交后的刷新提醒。 */
@Service
public class NotificationWriter {
    private final NotificationEventMapper events;
    private final NotificationMapper notifications;
    private final WorkspaceMapper workspaces;
    private final NotificationCodec codec;
    private final NotificationProperties properties;

    public NotificationWriter(NotificationEventMapper events, NotificationMapper notifications,
            WorkspaceMapper workspaces, NotificationCodec codec, NotificationProperties properties) {
        this.events = events;
        this.notifications = notifications;
        this.workspaces = workspaces;
        this.codec = codec;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeRequired(NotificationCommand request) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = Instant.now();
        NotificationCommand command = codec.normalize(request, now);
        if (workspaces.lockWorkspace(command.workspaceId()) == null) {
            throw new NotFoundException("通知所属工作空间不存在");
        }
        NotificationEvent event = new NotificationEvent();
        event.setEventId(command.eventId());
        event.setWorkspaceId(command.workspaceId());
        event.setFingerprint(codec.fingerprint(codec.encode(command)));
        event.setCreatedAt(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
        if (events.insert(event) == 0) {
            if (!event.getFingerprint().equals(events.lock(command.eventId()).getFingerprint())) {
                throw new IllegalArgumentException("同一通知事件标识不能对应不同内容");
            }
            return;
        }
        for (String recipient : notifications.eligibleRecipients(command.workspaceId(), command.recipients())) {
            notifications.ensureInbox(recipient);
            long sequence = notifications.lockInbox(recipient) + 1;
            Notification notification = create(command, recipient, sequence, event.getCreatedAt());
            notifications.insert(notification);
            notifications.advance(recipient);
        }
    }

    private Notification create(NotificationCommand command, String recipient, long sequence, LocalDateTime now) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setEventId(command.eventId());
        notification.setRecipient(recipient);
        notification.setSequence(sequence);
        notification.setWorkspaceId(command.workspaceId());
        notification.setType(command.type());
        notification.setTitle(command.title());
        notification.setBody(command.body());
        notification.setResourceType(command.resourceType());
        notification.setResourceId(command.resourceId());
        notification.setCreatedAt(now);
        return notification;
    }
}
