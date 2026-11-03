package com.mike.workstation.dto;
import com.mike.workstation.domain.WorkspaceRole;
import java.time.OffsetDateTime;

/** 管理与预览响应均不返回邀请凭据。 */
public record InvitationResponse(String id, Long workspaceId, String workspaceName, String email,
        WorkspaceRole role, String status, String mailStatus,
        OffsetDateTime createdAt, OffsetDateTime expiresAt) {}
