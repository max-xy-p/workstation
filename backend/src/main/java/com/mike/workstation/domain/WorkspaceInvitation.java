package com.mike.workstation.domain;

import lombok.Data;
import java.time.LocalDateTime;

/** 邀请与投递元数据，凭据仅保存摘要，不保存可直接使用的链接。 */
@Data
public class WorkspaceInvitation {
    private String id;
    private Long workspaceId;
    private String workspaceName;
    private String email;
    private WorkspaceRole role;
    private String status;
    private String createdBy;
    private String requestId;
    private String tokenHash;
    private String acceptedBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String mailStatus;
    private String deliveryId;
}
