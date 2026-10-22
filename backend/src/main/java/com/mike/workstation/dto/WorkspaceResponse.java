package com.mike.workstation.dto;

import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.domain.WorkspaceType;

import java.time.OffsetDateTime;
import java.util.List;

/** 当前主体可访问的工作空间及其有效权限。 */
public record WorkspaceResponse(
        Long id,
        String name,
        WorkspaceType type,
        WorkspaceRole role,
        List<WorkspacePermission> permissions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
