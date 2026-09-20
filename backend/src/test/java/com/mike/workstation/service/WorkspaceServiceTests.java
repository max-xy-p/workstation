package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspaceMember;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.dto.WorkspaceMemberRequest;
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

        var request = new WorkspaceMemberRequest(
                "subject-2", PrincipalType.USER, "member", "Member", WorkspaceRole.OWNER);

        assertThatThrownBy(() -> workspaceService.addMember(WORKSPACE_ID, request))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        verify(auditService).denied(WORKSPACE_ID, "MEMBER_MANAGE", "WORKSPACE_MEMBER", null);
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
