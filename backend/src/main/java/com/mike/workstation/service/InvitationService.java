package com.mike.workstation.service;

import com.mike.workstation.assembler.InvitationResponseAssembler;
import com.mike.workstation.assembler.WorkspaceResponseAssembler;
import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.SecurityPrincipal;
import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspaceInvitation;
import com.mike.workstation.domain.WorkspaceMember;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.domain.WorkspaceType;
import com.mike.workstation.dto.InvitationRequest;
import com.mike.workstation.dto.InvitationTokenRequest;
import com.mike.workstation.dto.InvitationResponse;
import com.mike.workstation.dto.WorkspaceResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.InvitationConflictException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.InvitationMapper;
import com.mike.workstation.mapper.WorkspaceMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** 锁定空间后校验邀请状态，与现有成员变更保持相同锁顺序。 */
@Service
public class InvitationService {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}$");
    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String WORKSPACE_INVITATION = "WORKSPACE_INVITATION";
    private static final String INVALID = "邀请不存在或凭据无效";
    private final InvitationMapper mapper;
    private final WorkspaceMapper workspaces;
    private final WorkspaceAuthorizationService authorization;
    private final CurrentPrincipalResolver principals;
    private final InvitationTokenService tokens;
    private final InvitationProperties properties;
    private final AuditService audit;
    private final WorkspaceNotifications notifications;

    public InvitationService(InvitationMapper mapper, WorkspaceMapper workspaces,
            WorkspaceAuthorizationService authorization, CurrentPrincipalResolver principals,
            InvitationTokenService tokens, InvitationProperties properties, AuditService audit,
            WorkspaceNotifications notifications) {
        this.mapper = mapper;
        this.workspaces = workspaces;
        this.authorization = authorization;
        this.principals = principals;
        this.tokens = tokens;
        this.properties = properties;
        this.audit = audit;
        this.notifications = notifications;
    }

    public List<InvitationResponse> list(Long workspaceId) {
        authorization.require(workspaceId, WorkspacePermission.MEMBER_MANAGE);
        requireEnabled();
        return mapper.list(workspaceId).stream().map(InvitationResponseAssembler::toResponse).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvitationResponse create(Long workspaceId, InvitationRequest request) {
        requireEnabled();
        workspaces.lockWorkspace(workspaceId);
        Workspace workspace = authorization.require(workspaceId, WorkspacePermission.MEMBER_MANAGE);
        CurrentPrincipal actor = principals.require();
        if (workspace.getType() != WorkspaceType.SHARED || actor.principalType() != PrincipalType.USER) {
            throw new BusinessException("只有用户可以向共享空间发送邀请");
        }
        RequestChecks.requiredObject(request, "邀请请求");
        String requestId = canonicalId(request.requestId());
        String email = normalizeEmail(request.email());
        if (request.role() == null || request.role() == WorkspaceRole.OWNER) {
            throw new BusinessException("邀请角色必须为管理员、编辑者或只读成员");
        }
        WorkspaceInvitation existing = mapper.findRequest(workspaceId, actor.subjectId(), requestId);
        if (existing != null) {
            if (!email.equals(existing.getEmail()) || request.role() != existing.getRole()) {
                throw new InvitationConflictException("请求标识已用于不同的邀请内容");
            }
            return InvitationResponseAssembler.toResponse(existing);
        }
        LocalDateTime now = now();
        if (mapper.countPending(workspaceId, now) >= 100
                || mapper.countEmail(workspaceId, email, now) > 0) {
            throw new BusinessException("该邮箱已有待接受邀请，或空间待接受邀请已达上限");
        }
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.setId(UUID.randomUUID().toString());
        invitation.setDeliveryId(UUID.randomUUID().toString());
        invitation.setWorkspaceId(workspaceId);
        invitation.setWorkspaceName(workspace.getName());
        invitation.setEmail(email);
        invitation.setRole(request.role());
        invitation.setCreatedBy(actor.subjectId());
        invitation.setRequestId(requestId);
        invitation.setTokenHash(tokens.hash(tokens.token(invitation.getId())));
        invitation.setStatus(PENDING);
        invitation.setMailStatus(PENDING);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusHours(23));
        mapper.insert(invitation);
        mapper.insertOutbox(invitation);
        audit.success(workspaceId, "INVITATION_CREATE", WORKSPACE_INVITATION, null);
        return InvitationResponseAssembler.toResponse(invitation);
    }

    public InvitationResponse preview(InvitationTokenRequest request) {
        requireEnabled();
        return InvitationResponseAssembler.toResponse(resolve(request));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkspaceResponse accept(InvitationTokenRequest request) {
        requireEnabled();
        WorkspaceInvitation candidate = resolve(request);
        workspaces.lockWorkspace(candidate.getWorkspaceId());
        // 在获得空间锁后重新读取，串行化接受、撤销及成员角色变更。
        WorkspaceInvitation invitation = mapper.lock(candidate.getId());
        if (invitation == null) {
            throw new NotFoundException(INVALID);
        }
        String subject = principals.require().subjectId();
        WorkspaceMember member = workspaces.findMember(invitation.getWorkspaceId(), subject);
        if (ACCEPTED.equals(invitation.getStatus())) {
            if (!subject.equals(invitation.getAcceptedBy()) || member == null) {
                throw new BusinessException("邀请已使用，不能重新授予成员权限");
            }
            return memberWorkspace(invitation.getWorkspaceId(), subject);
        }
        if (!PENDING.equals(invitation.getStatus()) || !invitation.getExpiresAt().isAfter(now())) {
            throw new BusinessException("邀请已过期或已撤销");
        }
        // 邀请人失去管理权限时，不允许旧邀请继续授予权限。
        Workspace inviter = workspaces.findForPrincipal(invitation.getWorkspaceId(), invitation.getCreatedBy());
        if (inviter == null || !WorkspacePermission.forRole(inviter.getRole()).contains(WorkspacePermission.MEMBER_MANAGE)) {
            throw new BusinessException("邀请人已失去成员管理权限，请联系空间管理员重新邀请");
        }
        if (member == null) {
            CurrentPrincipal actor = principals.require();
            SecurityPrincipal projection = new SecurityPrincipal();
            projection.setSubjectId(subject);
            projection.setPrincipalType(PrincipalType.USER);
            projection.setUsername(actor.username());
            projection.setDisplayName(actor.displayName());
            projection.setCreatedAt(now());
            projection.setUpdatedAt(now());
            workspaces.upsertPrincipal(projection);
            member = new WorkspaceMember();
            member.setWorkspaceId(invitation.getWorkspaceId());
            member.setSubjectId(subject);
            member.setRole(invitation.getRole());
            member.setJoinedAt(now());
            workspaces.insertMember(member);
        }
        if (mapper.transition(invitation.getId(), ACCEPTED, subject) != 1) {
            throw new InvitationConflictException("邀请状态已变化，请重新打开邀请");
        }
        mapper.cancel(invitation.getId());
        notifications.invitationAccepted(invitation.getWorkspaceId(), inviter.getName(), invitation.getId(),
                invitation.getCreatedBy(), subject);
        audit.success(invitation.getWorkspaceId(), "INVITATION_ACCEPT", WORKSPACE_INVITATION, null);
        return memberWorkspace(invitation.getWorkspaceId(), subject);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvitationResponse revoke(Long workspaceId, String id) {
        workspaces.lockWorkspace(workspaceId);
        authorization.require(workspaceId, WorkspacePermission.MEMBER_MANAGE);
        WorkspaceInvitation invitation = mapper.find(canonicalId(id));
        if (invitation == null || !workspaceId.equals(invitation.getWorkspaceId())) {
            throw new NotFoundException(INVALID);
        }
        if (ACCEPTED.equals(invitation.getStatus())) {
            throw new BusinessException("邀请已接受，请通过成员管理移除成员");
        }
        mapper.transition(id, "REVOKED", null);
        mapper.cancel(id);
        audit.success(workspaceId, "INVITATION_REVOKE", WORKSPACE_INVITATION, null);
        return InvitationResponseAssembler.toResponse(mapper.find(id));
    }

    private WorkspaceInvitation resolve(InvitationTokenRequest request) {
        if (request == null || request.token() == null || request.token().length() != 80
                || request.token().charAt(36) != '.') {
            throw new NotFoundException(INVALID);
        }
        String id;
        try {
            id = canonicalId(request.token().substring(0, 36));
        } catch (IllegalArgumentException exception) {
            throw new NotFoundException(INVALID);
        }
        WorkspaceInvitation invitation = mapper.find(id);
        if (invitation == null || !tokens.hash(request.token()).equals(invitation.getTokenHash())) {
            throw new NotFoundException(INVALID);
        }
        if (!normalizeEmail(principals.requireVerifiedEmail()).equals(invitation.getEmail())) {
            throw new WorkspaceAccessDeniedException("请使用受邀且已验证邮箱的账号接受邀请");
        }
        return invitation;
    }

    private WorkspaceResponse memberWorkspace(Long id, String subject) {
        return WorkspaceResponseAssembler.toResponse(workspaces.findForPrincipal(id, subject));
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException("邮箱邀请尚未启用");
        }
    }

    private static String canonicalId(String value) {
        if (value == null || !UUID.fromString(value).toString().equals(value)) {
            throw new IllegalArgumentException("请求标识必须为规范 UUID");
        }
        return value;
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.trim().length() > 254 || !EMAIL.matcher(value.trim()).matches()) {
            throw new BusinessException("邮箱格式不合法");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String local = normalized.substring(0, normalized.indexOf('@'));
        String domain = normalized.substring(normalized.indexOf('@') + 1);
        if (local.length() > 64 || local.startsWith(".") || local.endsWith(".")
                || local.contains("..")
                || Arrays.stream(domain.split("\\.", -1)).anyMatch(InvitationService::invalidDomainLabel)) {
            throw new BusinessException("邮箱格式不合法");
        }
        return normalized;
    }

    private static boolean invalidDomainLabel(String label) {
        return label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-");
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault()).withNano(0);
    }
}
