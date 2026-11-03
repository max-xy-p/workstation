package com.mike.workstation.service;

import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.domain.WorkspaceInvitation;
import com.mike.workstation.mapper.InvitationMapper;
import com.mike.workstation.messaging.InvitationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** 发布确认不等于邮件服务已接收；未完成记录一分钟后补发，容忍消息和确认丢失。 */
@Component
public class InvitationOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(InvitationOutboxRelay.class);
    private final InvitationMapper mapper;
    private final InvitationPublisher publisher;
    private final InvitationProperties properties;

    public InvitationOutboxRelay(InvitationMapper mapper, InvitationPublisher publisher,
            InvitationProperties properties) {
        this.mapper = mapper;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        if (!properties.enabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        for (String id : mapper.due(now)) {
            relayOne(id, now);
        }
    }

    public void relayOne(String id, LocalDateTime now) {
        String lease = UUID.randomUUID().toString();
        if (mapper.claim(id, lease, now, now.plusSeconds(30)) != 1) {
            return;
        }
        WorkspaceInvitation invitation = mapper.findDelivery(id);
        String state = "PENDING";
        try {
            if (!invitation.getExpiresAt().isAfter(now)) {
                state = "EXPIRED";
            } else if (!"PENDING".equals(invitation.getStatus())) {
                state = "CANCELLED";
            } else {
                publisher.publish(id);
                state = "PUBLISHED";
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("邀请发布已中断");
        } catch (Exception exception) {
            // 不记录异常原文，以免客户端或驱动携带敏感上下文。
            log.warn("邀请发布暂时失败，将自动补偿");
        }
        mapper.published(id, lease, state, now.plusSeconds(60));
    }
}
