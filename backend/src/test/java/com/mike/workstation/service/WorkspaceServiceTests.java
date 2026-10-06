package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspaceMember;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.domain.SecurityPrincipal;
import com.mike.workstation.domain.WorkspaceType;
import com.mike.workstation.dto.WorkspaceMemberRequest;
import com.mike.workstation.dto.WorkspaceRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.WorkspaceMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private CurrentPrincipalResolver principalResolver;
    @Mock
    private WorkspaceAuthorizationService authorization;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private WorkspaceService workspaceService;

    @Test
    void firstWorkspaceListCreatesAndAuditsPersonalWorkspace() {
        var principal = new CurrentPrincipal(
                "subject-1", PrincipalType.USER, "mike", "Mike", Set.of("WORKSTATION_USER"));
        when(principalResolver.require()).thenReturn(principal);
        doAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            workspace.setId(WORKSPACE_ID);
            return 1;
        }).when(workspaceMapper).insertPersonalWorkspace(any(Workspace.class));
        when(workspaceMapper.listForPrincipal("subject-1")).thenReturn(List.of());

        workspaceService.list();

        verify(workspaceMapper).insertMember(any(WorkspaceMember.class));
        verify(auditService).success(WORKSPACE_ID, "WORKSPACE_CREATE", "WORKSPACE", WORKSPACE_ID);
    }

    @Test
    void adminCannotPromoteAnotherMemberToOwner() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenReturn(workspace(WorkspaceRole.ADMIN));

        var request = new WorkspaceMemberRequest("subject-2", WorkspaceRole.OWNER);

        assertThatThrownBy(() -> workspaceService.addMember(WORKSPACE_ID, request))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        verify(auditService).denied(WORKSPACE_ID, "MEMBER_MANAGE", "WORKSPACE_MEMBER", null);
    }

    @Test
    void createSharedWorkspaceAddsCreatorAsOwner() {
        var principal = new CurrentPrincipal(
                "subject-1", PrincipalType.USER, "mike", "Mike", Set.of("WORKSTATION_USER"));
        when(principalResolver.require()).thenReturn(principal);
        doAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            assertThat(workspace.getType()).isEqualTo(WorkspaceType.SHARED);
            workspace.setId(WORKSPACE_ID);
            return 1;
        }).when(workspaceMapper).insertSharedWorkspace(any(Workspace.class));
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.WORKSPACE_READ))
                .thenReturn(workspace(WorkspaceRole.OWNER));

        workspaceService.createShared(new WorkspaceRequest("Team"));

        verify(workspaceMapper).insertMember(any(WorkspaceMember.class));
        verify(auditService).success(WORKSPACE_ID, "WORKSPACE_CREATE", "WORKSPACE", WORKSPACE_ID);
    }

    @Test
    void addingUnknownMemberDoesNotCreatePrincipal() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenReturn(workspace(WorkspaceRole.OWNER));

        assertThatThrownBy(() -> workspaceService.addMember(
                WORKSPACE_ID, new WorkspaceMemberRequest("unknown", WorkspaceRole.EDITOR)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主体不存在");
    }

    @Test
    void addingKnownMemberUsesServerPrincipal() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenReturn(workspace(WorkspaceRole.OWNER));
        SecurityPrincipal principal = new SecurityPrincipal();
        principal.setSubjectId("subject-2");
        principal.setPrincipalType(PrincipalType.USER);
        when(workspaceMapper.findPrincipal("subject-2")).thenReturn(principal);
        when(workspaceMapper.findMember(WORKSPACE_ID, "subject-2"))
                .thenReturn(null, member("subject-2", WorkspaceRole.EDITOR));

        workspaceService.addMember(WORKSPACE_ID, new WorkspaceMemberRequest("subject-2", WorkspaceRole.EDITOR));

        verify(workspaceMapper).insertMember(any(WorkspaceMember.class));
    }

    @Test
    void lastOwnerCannotBeRemoved() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenReturn(workspace(WorkspaceRole.OWNER));
        when(workspaceMapper.findMember(WORKSPACE_ID, "subject-1"))
                .thenReturn(member("subject-1", WorkspaceRole.OWNER));
        when(workspaceMapper.countOwners(WORKSPACE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> workspaceService.removeMember(WORKSPACE_ID, "subject-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最后一个 Owner");
    }

    @Test
    void lastOwnerCannotBeDemoted() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenReturn(workspace(WorkspaceRole.OWNER));
        when(workspaceMapper.findMember(WORKSPACE_ID, "subject-1"))
                .thenReturn(member("subject-1", WorkspaceRole.OWNER));
        when(workspaceMapper.countOwners(WORKSPACE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> workspaceService.updateMemberRole(
                WORKSPACE_ID, "subject-1", WorkspaceRole.EDITOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最后一个 Owner");
        verify(workspaceMapper, never()).updateMemberRole(WORKSPACE_ID, "subject-1", WorkspaceRole.EDITOR);
    }

    @Test
    void workspaceRenamePersistsTrimmedNameWithinAuthorizedWorkspace() {
        Workspace workspace = workspace(WorkspaceRole.OWNER);
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.WORKSPACE_WRITE))
                .thenReturn(workspace);
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.WORKSPACE_READ))
                .thenReturn(workspace);

        var renamed = workspaceService.update(WORKSPACE_ID, new WorkspaceRequest("  Team Space  "));

        assertThat(renamed.name()).isEqualTo("Team Space");
        verify(workspaceMapper).updateWorkspace(workspace);
        verify(auditService).success(WORKSPACE_ID, "WORKSPACE_UPDATE", "WORKSPACE", WORKSPACE_ID);
    }

    @Test
    void ownerCanPromoteEditorAndReceivesUpdatedMember() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenReturn(workspace(WorkspaceRole.OWNER));
        when(workspaceMapper.findMember(WORKSPACE_ID, "subject-2"))
                .thenReturn(member("subject-2", WorkspaceRole.EDITOR),
                        member("subject-2", WorkspaceRole.ADMIN));

        var updated = workspaceService.updateMemberRole(
                WORKSPACE_ID, "subject-2", WorkspaceRole.ADMIN);

        assertThat(updated.role()).isEqualTo(WorkspaceRole.ADMIN);
        verify(workspaceMapper).updateMemberRole(WORKSPACE_ID, "subject-2", WorkspaceRole.ADMIN);
        verify(auditService).success(WORKSPACE_ID, "MEMBER_ROLE_UPDATE", "WORKSPACE_MEMBER", null);
    }

    @Test
    void removingEditorDeletesOnlyMembership() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenReturn(workspace(WorkspaceRole.ADMIN));
        when(workspaceMapper.findMember(WORKSPACE_ID, "subject-2"))
                .thenReturn(member("subject-2", WorkspaceRole.EDITOR));

        workspaceService.removeMember(WORKSPACE_ID, "subject-2");

        verify(workspaceMapper).deleteMember(WORKSPACE_ID, "subject-2");
        verify(auditService).success(WORKSPACE_ID, "MEMBER_REMOVE", "WORKSPACE_MEMBER", null);
    }

    @Test
    void memberCandidateLookupUsesNormalizedKeyword() {
        SecurityPrincipal candidate = new SecurityPrincipal();
        candidate.setSubjectId("subject-2");
        candidate.setUsername("alex");
        candidate.setDisplayName("Alex");
        when(workspaceMapper.findUsersByUsername("alex")).thenReturn(List.of(candidate));

        var results = workspaceService.findMemberCandidates(WORKSPACE_ID, " alex ");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).subjectId()).isEqualTo("subject-2");
        verify(workspaceMapper).findUsersByUsername("alex");
    }

    @Test
    void memberSearchRejectsBlankAndOverlongKeywordsBeforeQuerying() {
        assertThatThrownBy(() -> workspaceService.findMemberCandidates(WORKSPACE_ID, "  "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> workspaceService.findMemberCandidates(WORKSPACE_ID, "a".repeat(256)))
                .isInstanceOf(BusinessException.class);
        verify(workspaceMapper, never()).findUsersByUsername(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void memberSearchChecksPermissionBeforeLookingUpUsers() {
        when(authorization.require(WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .thenThrow(new WorkspaceAccessDeniedException("没有成员管理权限"));
        assertThatThrownBy(() -> workspaceService.findMemberCandidates(WORKSPACE_ID, "张"))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        verify(workspaceMapper, never()).findUsersByUsername(org.mockito.ArgumentMatchers.anyString());
    }

    private Workspace workspace(WorkspaceRole role) {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setRole(role);
        return workspace;
    }

    private WorkspaceMember member(String subjectId, WorkspaceRole role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(WORKSPACE_ID);
        member.setSubjectId(subjectId);
        member.setRole(role);
        return member;
    }
}
