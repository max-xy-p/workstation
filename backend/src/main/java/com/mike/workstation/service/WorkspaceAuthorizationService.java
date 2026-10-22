package com.mike.workstation.service;

import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.WorkspaceMapper;
import com.mike.workstation.security.CurrentPrincipalResolver;

import org.springframework.stereotype.Service;

/** 集中执行 Workspace 成员和 Permission 校验。 */
@Service
public class WorkspaceAuthorizationService {
    private final WorkspaceMapper workspaceMapper;
    private final CurrentPrincipalResolver principalResolver;
    private final AuditService auditService;

    public WorkspaceAuthorizationService(
            WorkspaceMapper workspaceMapper,
            CurrentPrincipalResolver principalResolver,
            AuditService auditService) {
        this.workspaceMapper = workspaceMapper;
        this.principalResolver = principalResolver;
        this.auditService = auditService;
    }

    public Workspace require(Long workspaceId, WorkspacePermission permission) {
        var principal = principalResolver.require();
        Workspace workspace = workspaceMapper.findForPrincipal(workspaceId, principal.subjectId());
        if (workspace == null) {
            auditService.denied(workspaceId, permission.name(), "WORKSPACE", workspaceId);
            throw new NotFoundException("工作空间不存在");
        }
        if (!WorkspacePermission.forRole(workspace.getRole()).contains(permission)) {
            auditService.denied(workspaceId, permission.name(), "WORKSPACE", workspaceId);
            throw new WorkspaceAccessDeniedException("当前成员没有执行该操作的权限");
        }
        return workspace;
    }
}
