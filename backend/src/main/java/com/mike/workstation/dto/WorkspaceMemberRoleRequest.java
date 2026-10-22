package com.mike.workstation.dto;

import com.mike.workstation.domain.WorkspaceRole;

/** 更新工作空间成员角色的请求。 */
public record WorkspaceMemberRoleRequest(WorkspaceRole role) {}
