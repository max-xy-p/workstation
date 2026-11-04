package com.mike.workstation.assembler;

import com.mike.workstation.domain.Notification;
import com.mike.workstation.dto.NotificationResponse;

/** 通知实体与公共响应分离。 */
public final class NotificationResponseAssembler {
    private NotificationResponseAssembler() {}

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getSequence(),
                notification.getWorkspaceId(), notification.getType(), notification.getTitle(),
                notification.getBody(), notification.getResourceType(), notification.getResourceId(),
                ApiTime.toOffset(notification.getCreatedAt()), ApiTime.toOffset(notification.getReadAt()));
    }
}
