package com.mike.workstation.domain;

import lombok.Data;
import java.time.LocalDateTime;

/** 每位接收者独立保存已读状态，序号由其收件箱事务锁分配。 */
@Data
public class Notification {
    private String id;
    private String eventId;
    private String recipient;
    private long sequence;
    private Long workspaceId;
    private String type;
    private String title;
    private String body;
    private NotificationResourceType resourceType;
    private Long resourceId;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
