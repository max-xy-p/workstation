package com.mike.workstation.service;

import com.mike.workstation.assembler.WorkspaceResponseAssembler;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.SecurityPrincipal;
import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspaceMember;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.domain.WorkspaceType;
import com.mike.workstation.dto.WorkspaceMemberRequest;
import com.mike.workstation.dto.WorkspaceMemberCandidate;
import com.mike.workstation.dto.WorkspaceMemberResponse;
import com.mike.workstation.dto.WorkspaceRequest;
import com.mike.workstation.dto.WorkspaceResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.WorkspaceMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** 工作空间初始化、查询和成员管理。 */
public class WorkspaceService {
    private final WorkspaceMapper workspaceMapper;
    private final CurrentPrincipalResolver principalResolver;
    private final WorkspaceAuthorizationService authorization;
    private final AuditService auditService;

    public WorkspaceService(
            WorkspaceMapper workspaceMapper,
            CurrentPrincipalResolver principalResolver,
            WorkspaceAuthorizationService authorization,
            AuditService auditService
    ) {
        this.workspaceMapper = workspaceMapper;
        this.principalResolver = principalResolver;
        this.authorization = authorization;
        this.auditService = auditService;
    }

    @Transactional
    public List<WorkspaceResponse> list() {
        CurrentPrincipal principal = principalResolver.require();
        upsertPrincipal(principal.subjectId(), principal.principalType(), principal.username(), principal.displayName());
        if (principal.principalType() == PrincipalType.USER) {
            ensurePersonalWorkspace(principal);
        }
        return workspaceMapper.listForPrincipal(principal.subjectId()).stream()
                .map(WorkspaceResponseAssembler::toResponse)
                .toList();
    }

    public WorkspaceResponse get(Long workspaceId) {
        return WorkspaceResponseAssembler.toResponse(
                authorization.require(workspaceId, WorkspacePermission.WORKSPACE_READ));
    }

    @Transactional
    public WorkspaceResponse createShared(WorkspaceRequest request) {
        CurrentPrincipal principal = principalResolver.require();
        if (principal.principalType() != PrincipalType.USER) {
            throw new BusinessException("只有用户可以创建共享工作空间");
        }
        RequestChecks.requiredObject(request, "工作空间请求");
        RequestChecks.requiredText(request.name(), "工作空间名称");
        String name = request.name().trim();
        RequestChecks.maxLength(name, 120, "工作空间名称");
        upsertPrincipal(principal.subjectId(), principal.principalType(), principal.username(), principal.displayName());
        LocalDateTime now = LocalDateTime.now();
        Workspace workspace = new Workspace();
        workspace.setName(name);
        workspace.setType(WorkspaceType.SHARED);
        workspace.setCreatedBySubject(principal.subjectId());
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspaceMapper.insertSharedWorkspace(workspace);
        WorkspaceMember owner = new WorkspaceMember();
        owner.setWorkspaceId(workspace.getId());
        owner.setSubjectId(principal.subjectId());
        owner.setRole(WorkspaceRole.OWNER);
        owner.setJoinedAt(now);
        workspaceMapper.insertMember(owner);
        auditService.success(workspace.getId(), "WORKSPACE_CREATE", "WORKSPACE", workspace.getId());
        return get(workspace.getId());
    }

    public List<WorkspaceMemberCandidate> findMemberCandidates(Long workspaceId, String username) {
        authorization.require(workspaceId, WorkspacePermission.MEMBER_MANAGE);
        RequestChecks.requiredText(username, "用户名");
        String exactUsername = username.trim();
        RequestChecks.maxLength(exactUsername, 255, "用户名");
        return workspaceMapper.findUsersByUsername(exactUsername).stream()
                .map(candidate -> new WorkspaceMemberCandidate(
                        candidate.getSubjectId(), candidate.getUsername(), candidate.getDisplayName()))
                .toList();
    }

    @Transactional
    public WorkspaceResponse update(Long workspaceId, WorkspaceRequest request) {
        Workspace workspace = authorization.require(workspaceId, WorkspacePermission.WORKSPACE_WRITE);
        RequestChecks.requiredObject(request, "工作空间请求");
        RequestChecks.requiredText(request.name(), "工作空间名称");
        RequestChecks.maxLength(request.name().trim(), 120, "工作空间名称");
        workspace.setName(request.name().trim());
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.updateWorkspace(workspace);
        auditService.success(workspaceId, "WORKSPACE_UPDATE", "WORKSPACE", workspaceId);
        return get(workspaceId);
    }

    public List<WorkspaceMemberResponse> listMembers(Long workspaceId) {
        authorization.require(workspaceId, WorkspacePermission.MEMBER_READ);
        return workspaceMapper.listMembers(workspaceId).stream()
                .map(WorkspaceResponseAssembler::toMemberResponse)
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse addMember(Long workspaceId, WorkspaceMemberRequest request) {
        workspaceMapper.lockWorkspace(workspaceId);
        Workspace actorWorkspace = authorization.require(workspaceId, WorkspacePermission.MEMBER_MANAGE);
        validateMemberRequest(request);
        if (request.role() == WorkspaceRole.OWNER && actorWorkspace.getRole() != WorkspaceRole.OWNER) {
            denyOwnerChange(workspaceId, "只有 Owner 可以添加 Owner");
        }
        if (workspaceMapper.findMember(workspaceId, request.subjectId()) != null) {
            throw new BusinessException("该主体已经是工作空间成员");
        }

        SecurityPrincipal candidate = workspaceMapper.findPrincipal(request.subjectId());
        if (candidate == null) {
            throw new BusinessException("主体不存在，请先登录或使用机器身份访问工作空间列表");
        }
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setSubjectId(request.subjectId());
        member.setRole(request.role());
        member.setJoinedAt(LocalDateTime.now());
        workspaceMapper.insertMember(member);
        auditService.success(workspaceId, "MEMBER_ADD", "WORKSPACE_MEMBER", null);
        return WorkspaceResponseAssembler.toMemberResponse(
                workspaceMapper.findMember(workspaceId, request.subjectId()));
    }

    @Transactional
    public WorkspaceMemberResponse updateMemberRole(Long workspaceId, String subjectId, WorkspaceRole role) {
        workspaceMapper.lockWorkspace(workspaceId);
        Workspace actorWorkspace = authorization.require(workspaceId, WorkspacePermission.MEMBER_MANAGE);
        RequestChecks.requiredText(subjectId, "主体标识");
        RequestChecks.requiredObject(role, "成员角色");
        WorkspaceMember target = workspaceMapper.findMember(workspaceId, subjectId);
        RequestChecks.requiredExisting(target, "工作空间成员不存在");
        requireOwnerForOwnerChange(workspaceId, actorWorkspace, target.getRole(), role);
        if (target.getRole() == WorkspaceRole.OWNER && role != WorkspaceRole.OWNER
                && workspaceMapper.countOwners(workspaceId) <= 1) {
            throw new BusinessException("不能降级工作空间的最后一个 Owner");
        }
        workspaceMapper.updateMemberRole(workspaceId, subjectId, role);
        auditService.success(workspaceId, "MEMBER_ROLE_UPDATE", "WORKSPACE_MEMBER", null);
        return WorkspaceResponseAssembler.toMemberResponse(workspaceMapper.findMember(workspaceId, subjectId));
    }

    @Transactional
    public void removeMember(Long workspaceId, String subjectId) {
        workspaceMapper.lockWorkspace(workspaceId);
        Workspace actorWorkspace = authorization.require(workspaceId, WorkspacePermission.MEMBER_MANAGE);
        WorkspaceMember target = workspaceMapper.findMember(workspaceId, subjectId);
        RequestChecks.requiredExisting(target, "工作空间成员不存在");
        requireOwnerForOwnerChange(workspaceId, actorWorkspace, target.getRole(), null);
        if (target.getRole() == WorkspaceRole.OWNER && workspaceMapper.countOwners(workspaceId) <= 1) {
            throw new BusinessException("不能移除工作空间的最后一个 Owner");
        }
        workspaceMapper.deleteMember(workspaceId, subjectId);
        auditService.success(workspaceId, "MEMBER_REMOVE", "WORKSPACE_MEMBER", null);
    }

    private void ensurePersonalWorkspace(CurrentPrincipal principal) {
        Workspace workspace = workspaceMapper.findPersonalByOwner(principal.subjectId());
        boolean created = workspace == null;
        LocalDateTime now = LocalDateTime.now();
        if (created) {
            workspace = new Workspace();
            workspace.setName(personalWorkspaceName(principal));
            workspace.setType(WorkspaceType.PERSONAL);
            workspace.setPersonalOwnerSubject(principal.subjectId());
            workspace.setCreatedBySubject(principal.subjectId());
            workspace.setCreatedAt(now);
            workspace.setUpdatedAt(now);
            workspaceMapper.insertPersonalWorkspace(workspace);
        }

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspace.getId());
        member.setSubjectId(principal.subjectId());
        member.setRole(WorkspaceRole.OWNER);
        member.setJoinedAt(now);
        workspaceMapper.insertMember(member);
        if (created) {
            auditService.success(workspace.getId(), "WORKSPACE_CREATE", "WORKSPACE", workspace.getId());
        }
    }

    private void upsertPrincipal(
            String subjectId, PrincipalType type, String username, String displayName
    ) {
        SecurityPrincipal principal = new SecurityPrincipal();
        principal.setSubjectId(subjectId);
        principal.setPrincipalType(type);
        principal.setUsername(username);
        principal.setDisplayName(displayName);
        LocalDateTime now = LocalDateTime.now();
        principal.setCreatedAt(now);
        principal.setUpdatedAt(now);
        workspaceMapper.upsertPrincipal(principal);
    }

    private void validateMemberRequest(WorkspaceMemberRequest request) {
        RequestChecks.requiredObject(request, "成员请求");
        RequestChecks.requiredText(request.subjectId(), "主体标识");
        RequestChecks.maxLength(request.subjectId(), 255, "主体标识");
        RequestChecks.requiredObject(request.role(), "成员角色");
    }

    private void requireOwnerForOwnerChange(
            Long workspaceId, Workspace actorWorkspace, WorkspaceRole currentRole, WorkspaceRole nextRole
    ) {
        if ((currentRole == WorkspaceRole.OWNER || nextRole == WorkspaceRole.OWNER)
                && actorWorkspace.getRole() != WorkspaceRole.OWNER) {
            denyOwnerChange(workspaceId, "只有 Owner 可以变更 Owner 成员");
        }
    }

    private void denyOwnerChange(Long workspaceId, String message) {
        auditService.denied(workspaceId, "MEMBER_MANAGE", "WORKSPACE_MEMBER", null);
        throw new WorkspaceAccessDeniedException(message);
    }

    private String personalWorkspaceName(CurrentPrincipal principal) {
        String base = principal.displayName();
        if (base == null || base.isBlank()) {
            base = principal.username();
        }
        if (base == null || base.isBlank()) {
            base = "我的";
        }
        String name = base.trim() + "的知识库";
        return name.length() <= 120 ? name : name.substring(0, 120);
    }
}
