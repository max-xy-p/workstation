package com.mike.workstation.service;

import com.mike.workstation.domain.NotificationResourceType;
import com.mike.workstation.domain.WorkspaceRole;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 把空间成员事件转换为事务内的个人通知。 */
@Component
public class WorkspaceNotifications {
    private final NotificationWriter writer;

    public WorkspaceNotifications(NotificationWriter writer) {
        this.writer = writer;
    }

    public void invitationAccepted(Long workspaceId, String workspaceName, String invitationId,
            String inviter, String accepter) {
        String eventId = UUID.nameUUIDFromBytes(
                ("WORKSPACE_INVITATION_ACCEPTED:" + invitationId).getBytes(StandardCharsets.UTF_8)).toString();
        write(eventId, workspaceId, inviter, accepter, "WORKSPACE_INVITATION_ACCEPTED",
                "空间邀请已接受", "你发出的「" + workspaceName + "」邀请已被接受。");
    }

    public void memberAdded(Long workspaceId, String workspaceName, String recipient, String actor,
            WorkspaceRole role) {
        write(UUID.randomUUID().toString(), workspaceId, recipient, actor, "WORKSPACE_MEMBER_ADDED",
                "你已加入工作空间", "你已加入「" + workspaceName + "」，角色为" + label(role) + "。");
    }

    public void memberRoleChanged(Long workspaceId, String workspaceName, String recipient, String actor,
            WorkspaceRole previous, WorkspaceRole current) {
        write(UUID.randomUUID().toString(), workspaceId, recipient, actor, "WORKSPACE_MEMBER_ROLE_CHANGED",
                "工作空间角色已变更",
                "你在「" + workspaceName + "」的角色由" + label(previous) + "变更为" + label(current) + "。");
    }

    private void write(String eventId, Long workspaceId, String recipient, String actor,
            String type, String title, String body) {
        if (!recipient.equals(actor)) {
            writer.writeRequired(new NotificationCommand(eventId, workspaceId, type, title, body,
                    NotificationResourceType.WORKSPACE, null, Instant.now(), List.of(recipient)));
        }
    }

    private static String label(WorkspaceRole role) {
        return switch (role) {
            case OWNER -> "所有者";
            case ADMIN -> "管理员";
            case EDITOR -> "编辑者";
            case VIEWER -> "只读成员";
        };
    }
}
