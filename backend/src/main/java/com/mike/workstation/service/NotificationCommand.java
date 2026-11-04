package com.mike.workstation.service;

import com.mike.workstation.domain.NotificationResourceType;
import java.time.Instant;
import java.util.List;

/** 业务适配器将已授权的事件转成此命令；不提供浏览器创建通知的接口。 */
public record NotificationCommand(String eventId, Long workspaceId, String type,
        String title, String body, NotificationResourceType resourceType, Long resourceId,
        Instant occurredAt, List<String> recipients) {}
