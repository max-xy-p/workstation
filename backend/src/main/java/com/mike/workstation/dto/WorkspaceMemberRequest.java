package com.mike.workstation.dto;

import com.mike.workstation.domain.WorkspaceRole;

/** 添加工作空间成员时使用的主体标识和角色。 */
public record WorkspaceMemberRequest(String subjectId, WorkspaceRole role) {}
