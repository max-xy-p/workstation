package com.mike.workstation.domain;

import lombok.Data;
import java.time.LocalDateTime;

/** 仅保存已同步处理事件的去重元数据，不保存待生成通知任务。 */
@Data
public class NotificationEvent {
    private String eventId;
    private Long workspaceId;
    private String fingerprint;
    private LocalDateTime createdAt;
}
