package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** 安全主体在某个工作空间中的成员关系。 */
@Data
public class WorkspaceMember {
    private Long workspaceId;
    private String subjectId;
    private PrincipalType principalType;
    private String username;
    private String displayName;
    private WorkspaceRole role;
    private LocalDateTime joinedAt;
}
