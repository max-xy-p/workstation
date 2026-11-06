package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.InvitationRequest;
import com.mike.workstation.dto.InvitationTokenRequest;
import com.mike.workstation.exception.*;
import com.mike.workstation.mapper.InvitationMapper;
import com.mike.workstation.mapper.WorkspaceMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class InvitationServiceTests {
    private final InvitationMapper mapper = mock(InvitationMapper.class);
    private final WorkspaceMapper workspaces = mock(WorkspaceMapper.class);
    private final WorkspaceAuthorizationService authorization = mock(WorkspaceAuthorizationService.class);
    private final CurrentPrincipalResolver principal = mock(CurrentPrincipalResolver.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationWriter writer = mock(NotificationWriter.class);
    private final WorkspaceNotifications notifications = new WorkspaceNotifications(writer);
    private final InvitationProperties properties = new InvitationProperties(true, "https://work.example",
            Base64.getEncoder().encodeToString(new byte[32]), "http://mail:8082", "x".repeat(32));
    private final InvitationTokenService tokens = new InvitationTokenService(properties);
    private final InvitationService service = new InvitationService(mapper, workspaces, authorization,
            principal, tokens, properties, audit, notifications);
    private Workspace workspace;
    private WorkspaceInvitation invitation;
    private String requestId;

    @BeforeEach
    void setup() {
        workspace = new Workspace();
        workspace.setId(9L);
        workspace.setName("研发空间");
        workspace.setType(WorkspaceType.SHARED);
        workspace.setRole(WorkspaceRole.OWNER);
        workspace.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        workspace.setUpdatedAt(workspace.getCreatedAt());
        invitation = invitation();
        requestId = UUID.randomUUID().toString();
        when(principal.require()).thenReturn(new CurrentPrincipal("user", PrincipalType.USER, "用户", null, Set.of()));
        when(authorization.require(9L, WorkspacePermission.MEMBER_MANAGE)).thenReturn(workspace);
    }

    @Test
    void creationNormalizesEmailAndPersistsOutboxWithoutNetworkCall() {
        var response = service.create(9L, new InvitationRequest(requestId, " Guest@Example.com ", WorkspaceRole.EDITOR));
        assertThat(response.email()).isEqualTo("guest@example.com");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.mailStatus()).isEqualTo("PENDING");
        verify(mapper).insert(argThat(i -> i.getTokenHash().length() == 64
                && !i.getTokenHash().contains(i.getId()) && i.getExpiresAt().equals(i.getCreatedAt().plusHours(23))));
        verify(mapper).insertOutbox(any());
        verify(audit).success(9L, "INVITATION_CREATE", "WORKSPACE_INVITATION", null);
    }

    @Test
    void duplicateRequestReturnsExistingAndRejectsChangedContent() {
        when(mapper.findRequest(9L, "user", requestId)).thenReturn(invitation);
        var request = new InvitationRequest(requestId, invitation.getEmail(), invitation.getRole());
        assertThat(service.create(9L, request).id()).isEqualTo(invitation.getId());
        assertThatThrownBy(() -> service.create(9L, new InvitationRequest(requestId, "other@example.com", WorkspaceRole.VIEWER)))
                .isInstanceOf(InvitationConflictException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void rejectsDisabledPersonalServiceOwnerInvalidAndOverQuotaRequests() {
        var disabled = new InvitationService(mapper, workspaces, authorization, principal, tokens,
                new InvitationProperties(false, "", "", "", ""), audit, notifications);
        assertThatThrownBy(() -> disabled.create(9L, null)).isInstanceOf(BusinessException.class);
        workspace.setType(WorkspaceType.PERSONAL);
        assertThatThrownBy(() -> service.create(9L, null)).isInstanceOf(BusinessException.class);
        workspace.setType(WorkspaceType.SHARED);
        assertThatThrownBy(() -> service.create(9L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(9L, new InvitationRequest("bad", "g@example.com", WorkspaceRole.EDITOR)))
                .isInstanceOf(IllegalArgumentException.class);
        for (String email : List.of("", "bad", "a\r\n@example.com", "x".repeat(255) + "@example.com",
                ".guest@example.com", "guest..a@example.com", "guest@a..example.com",
                "guest@-example.com", "guest@" + "x".repeat(64) + ".com")) {
            assertThatThrownBy(() -> service.create(9L, new InvitationRequest(requestId, email, WorkspaceRole.EDITOR)))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.create(9L, new InvitationRequest(requestId, "g@example.com", WorkspaceRole.OWNER)))
                .isInstanceOf(BusinessException.class);
        when(mapper.countEmail(eq(9L), anyString(), any())).thenReturn(1);
        assertThatThrownBy(() -> service.create(9L, new InvitationRequest(requestId, "g@example.com", WorkspaceRole.EDITOR)))
                .isInstanceOf(BusinessException.class);
        when(mapper.countPending(eq(9L), any())).thenReturn(100);
        assertThatThrownBy(() -> service.create(9L, new InvitationRequest(requestId, "g@example.com", WorkspaceRole.EDITOR)))
                .isInstanceOf(BusinessException.class);
        when(principal.require()).thenReturn(new CurrentPrincipal("machine", PrincipalType.SERVICE, "机器", null, Set.of()));
        assertThatThrownBy(() -> service.create(9L, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void previewRequiresTokenAndMatchingVerifiedEmailWithoutConsuming() {
        when(mapper.find(invitation.getId())).thenReturn(invitation);
        when(principal.requireVerifiedEmail()).thenReturn("GUEST@example.com");
        assertThat(service.preview(token()).workspaceName()).isEqualTo("研发空间");
        verify(mapper, never()).transition(any(), any(), any());
        when(principal.requireVerifiedEmail()).thenReturn("other@example.com");
        assertThatThrownBy(() -> service.preview(token())).isInstanceOf(WorkspaceAccessDeniedException.class);
        assertThatThrownBy(() -> service.preview(null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.preview(new InvitationTokenRequest("x".repeat(80)))).isInstanceOf(NotFoundException.class);
        String tampered = tokens.token(invitation.getId());
        tampered = tampered.substring(0, 79) + (tampered.endsWith("a") ? "b" : "a");
        String changed = tampered;
        assertThatThrownBy(() -> service.preview(new InvitationTokenRequest(changed))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void acceptCreatesMemberConsumesInvitationAndDoesNotOverwriteExistingRole() {
        prepareAccept();
        assertThat(service.accept(token()).id()).isEqualTo(9L);
        verify(workspaces).upsertPrincipal(any());
        verify(workspaces).insertMember(argThat(m -> m.getRole() == WorkspaceRole.EDITOR && m.getSubjectId().equals("user")));
        verify(mapper).transition(invitation.getId(), "ACCEPTED", "user");
        verify(writer).writeRequired(argThat(command ->
                command.type().equals("WORKSPACE_INVITATION_ACCEPTED")
                        && command.recipients().equals(List.of("inviter"))
                        && !command.body().contains(invitation.getEmail())));
        reset(workspaces);
        when(workspaces.findForPrincipal(anyLong(), anyString())).thenReturn(workspace);
        when(workspaces.findMember(9L, "user")).thenReturn(new WorkspaceMember());
        service.accept(token());
        verify(workspaces, never()).insertMember(any());
        verify(workspaces, never()).updateMemberRole(any(), any(), any());
    }

    @Test
    void acceptedReplayCannotRestoreRemovedMembershipOrJoinAsAnotherSubject() {
        prepareAccept();
        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedBy("user");
        assertThatThrownBy(() -> service.accept(token())).isInstanceOf(BusinessException.class);
        when(workspaces.findMember(9L, "user")).thenReturn(new WorkspaceMember());
        assertThat(service.accept(token()).id()).isEqualTo(9L);
        invitation.setAcceptedBy("other-user");
        assertThatThrownBy(() -> service.accept(token())).isInstanceOf(BusinessException.class);
        verify(workspaces, never()).insertMember(any());
        verifyNoInteractions(writer);
    }

    @Test
    void expiredRevokedAndInviterPermissionLossCannotGrantMembership() {
        prepareAccept();
        invitation.setStatus("REVOKED");
        assertThatThrownBy(() -> service.accept(token())).isInstanceOf(BusinessException.class);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now(ZoneId.systemDefault()).minusSeconds(1));
        assertThat(service.preview(token()).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> service.accept(token())).isInstanceOf(BusinessException.class);
        invitation.setExpiresAt(LocalDateTime.now(ZoneId.systemDefault()).plusHours(1));
        when(workspaces.findForPrincipal(9L, "inviter")).thenReturn(null);
        assertThatThrownBy(() -> service.accept(token())).isInstanceOf(BusinessException.class);
        workspace.setRole(WorkspaceRole.VIEWER);
        when(workspaces.findForPrincipal(9L, "inviter")).thenReturn(workspace);
        assertThatThrownBy(() -> service.accept(token())).isInstanceOf(BusinessException.class);
        verify(workspaces, never()).insertMember(any());
    }

    @Test
    void listAndRevokeAreScopedAndAcceptedInvitationsCannotBeRevoked() {
        when(mapper.list(9L)).thenReturn(List.of(invitation));
        assertThat(service.list(9L)).hasSize(1);
        when(mapper.find(invitation.getId())).thenReturn(invitation);
        service.revoke(9L, invitation.getId());
        verify(mapper).cancel(invitation.getId());
        invitation.setStatus("ACCEPTED");
        assertThatThrownBy(() -> service.revoke(9L, invitation.getId())).isInstanceOf(BusinessException.class);
        invitation.setWorkspaceId(10L);
        assertThatThrownBy(() -> service.revoke(9L, invitation.getId())).isInstanceOf(NotFoundException.class);
    }

    private void prepareAccept() {
        when(mapper.find(invitation.getId())).thenReturn(invitation);
        when(mapper.lock(invitation.getId())).thenReturn(invitation);
        when(principal.requireVerifiedEmail()).thenReturn(invitation.getEmail());
        when(workspaces.findForPrincipal(anyLong(), anyString())).thenReturn(workspace);
        when(mapper.transition(invitation.getId(), "ACCEPTED", "user")).thenReturn(1);
    }

    private InvitationTokenRequest token() {
        return new InvitationTokenRequest(tokens.token(invitation.getId()));
    }

    private WorkspaceInvitation invitation() {
        WorkspaceInvitation i = new WorkspaceInvitation();
        i.setId(UUID.randomUUID().toString());
        i.setDeliveryId(UUID.randomUUID().toString());
        i.setWorkspaceId(9L);
        i.setWorkspaceName("研发空间");
        i.setEmail("guest@example.com");
        i.setRole(WorkspaceRole.EDITOR);
        i.setStatus("PENDING");
        i.setCreatedBy("inviter");
        i.setMailStatus("PENDING");
        i.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()).withNano(0));
        i.setExpiresAt(i.getCreatedAt().plusHours(23));
        i.setTokenHash(tokens.hash(tokens.token(i.getId())));
        return i;
    }
}
