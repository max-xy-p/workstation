package com.mike.workstation.dto;

import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.WorkspaceRole;

/** 添加工作空间成员时使用的主体投影和角色。 */
public record WorkspaceMemberRequest(
        String subjectId,
        PrincipalType principalType,
        String username,
        String displayName,
        WorkspaceRole role
) {
}
