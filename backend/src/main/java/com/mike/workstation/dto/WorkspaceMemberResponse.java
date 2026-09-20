package com.mike.workstation.dto;

import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.WorkspaceRole;
import java.time.OffsetDateTime;

/** 工作空间成员响应。 */
public record WorkspaceMemberResponse(
        String subjectId,
        PrincipalType principalType,
        String username,
        String displayName,
        WorkspaceRole role,
        OffsetDateTime joinedAt
) {
}
