package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.WorkspaceMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

@ExtendWith(MockitoExtension.class)
class WorkspaceAuthorizationServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock private WorkspaceMapper workspaceMapper;
    @Mock private CurrentPrincipalResolver principalResolver;
    @Mock private AuditService auditService;
    @InjectMocks private WorkspaceAuthorizationService authorizationService;

    @BeforeEach
    void setUpPrincipal() {
        when(principalResolver.require())
                .thenReturn(
                        new CurrentPrincipal(
                                "subject-1",
                                PrincipalType.USER,
                                "mike",
                                "Mike",
                                Set.of("WORKSTATION_USER")));
    }

    @Test
    void viewerCanReadButCannotWriteNotes() {
        Workspace workspace = workspace(WorkspaceRole.VIEWER);
        when(workspaceMapper.findForPrincipal(WORKSPACE_ID, "subject-1")).thenReturn(workspace);

        assertThat(authorizationService.require(WORKSPACE_ID, WorkspacePermission.NOTE_READ))
                .isSameAs(workspace);
        assertThatThrownBy(
                        () ->
                                authorizationService.require(
                                        WORKSPACE_ID, WorkspacePermission.NOTE_WRITE))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        verify(auditService).denied(WORKSPACE_ID, "NOTE_WRITE", "WORKSPACE", WORKSPACE_ID);
    }

    @Test
    void nonMemberIsHiddenAsNotFoundAndAudited() {
        when(workspaceMapper.findForPrincipal(WORKSPACE_ID, "subject-1")).thenReturn(null);

        assertThatThrownBy(
                        () ->
                                authorizationService.require(
                                        WORKSPACE_ID, WorkspacePermission.NOTE_READ))
                .isInstanceOf(NotFoundException.class);
        verify(auditService).denied(WORKSPACE_ID, "NOTE_READ", "WORKSPACE", WORKSPACE_ID);
    }

    @Test
    void editorCannotManageMembers() {
        when(workspaceMapper.findForPrincipal(WORKSPACE_ID, "subject-1"))
                .thenReturn(workspace(WorkspaceRole.EDITOR));

        assertThatThrownBy(
                        () ->
                                authorizationService.require(
                                        WORKSPACE_ID, WorkspacePermission.MEMBER_MANAGE))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
    }

    private Workspace workspace(WorkspaceRole role) {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setRole(role);
        return workspace;
    }
}
