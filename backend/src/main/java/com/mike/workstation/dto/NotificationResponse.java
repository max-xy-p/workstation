package com.mike.workstation.dto;

import com.mike.workstation.domain.NotificationResourceType;
import java.time.OffsetDateTime;

/** 返回当前用户有权访问的通知内容。 */
public record NotificationResponse(String id, long sequence, Long workspaceId, String type,
        String title, String body, NotificationResourceType resourceType, Long resourceId,
        OffsetDateTime createdAt, OffsetDateTime readAt) {}
