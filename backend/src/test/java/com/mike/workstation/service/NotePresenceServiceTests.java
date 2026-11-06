package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.config.NotePresenceProperties;
import com.mike.workstation.domain.NotePresenceState;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.WorkspaceMember;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.domain.WorkspaceRole;
import com.mike.workstation.dto.NotePresenceRequest;
import com.mike.workstation.exception.NotePresenceException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.NotePresenceMapper;
import com.mike.workstation.messaging.NotePresenceMessages;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class NotePresenceServiceTests {
    private final CurrentPrincipalResolver principals = mock(CurrentPrincipalResolver.class);
    private final WorkspaceAuthorizationService authorization = mock(WorkspaceAuthorizationService.class);
    private final NoteMapper notes = mock(NoteMapper.class);
    private final NotePresenceMapper members = mock(NotePresenceMapper.class);
    private final NotePresenceStore store = mock(NotePresenceStore.class);
    private final ObjectProvider<NotePresenceMessages> messages = mock(ObjectProvider.class);
    private final NotePresenceService service = service(true);

    private NotePresenceService service(boolean enabled) {
        return new NotePresenceService(new NotePresenceProperties(enabled), principals, authorization,
                notes, members, store, messages);
    }

    private void user() {
        when(principals.require()).thenReturn(new CurrentPrincipal("user", PrincipalType.USER, "name", null, Set.of()));
        when(notes.findVersionById(1L, 2L)).thenReturn(3L);
    }

    @Test
    void editingRequiresWritePermissionAndIdentityComesFromToken() {
        user();
        String session = UUID.randomUUID().toString();
        service.update(1, 2, session, new NotePresenceRequest(NotePresenceState.EDITING, 1));
        verify(authorization).require(1L, WorkspacePermission.NOTE_WRITE);
        verify(store).update(1, 2, "user", session, 1, NotePresenceState.EDITING);
        doThrow(new WorkspaceAccessDeniedException("只读")).when(authorization).require(1L, WorkspacePermission.NOTE_WRITE);
        assertThatThrownBy(() -> service.update(1, 2, session, new NotePresenceRequest(NotePresenceState.EDITING, 2)))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        service.update(1, 2, session, new NotePresenceRequest(NotePresenceState.LEFT, 3));
        verify(store).update(1, 2, "user", session, 3, NotePresenceState.LEFT);
    }

    @Test
    void everySnapshotFiltersRemovedMembersAndDowngradesReadOnlyUsers() {
        user();
        when(store.snapshot(1, 2)).thenReturn(Map.of("removed", NotePresenceState.EDITING,
                "viewer", NotePresenceState.EDITING, "editor", NotePresenceState.EDITING));
        var viewer = new WorkspaceMember();
        viewer.setSubjectId("viewer");
        viewer.setUsername("viewer-name");
        viewer.setRole(WorkspaceRole.VIEWER);
        var editor = new WorkspaceMember();
        editor.setSubjectId("editor");
        editor.setUsername("editor-name");
        editor.setRole(WorkspaceRole.EDITOR);
        when(members.currentMembers(eq(1L), any())).thenReturn(List.of(editor, viewer));
        var result = service.get(1, 2);
        assertThat(result.available()).isTrue();
        assertThat(result.participants()).extracting(p -> p.subjectId()).containsExactly("editor", "viewer");
        assertThat(result.participants().get(1).state()).isEqualTo(NotePresenceState.VIEWING);
        verify(authorization).require(1L, WorkspacePermission.NOTE_READ);
    }

    @Test
    void unavailableIsDifferentFromEmptyAndDisabledDoesNotAccessRedis() {
        user();
        when(store.snapshot(1, 2)).thenThrow(new DataAccessResourceFailureException("不得暴露连接配置"));
        assertThat(service.get(1, 2).available()).isFalse();
        assertThat(service.get(1, 2).participants()).isEmpty();
        assertThat(service(false).get(1, 2).enabled()).isFalse();
        assertThatThrownBy(() -> service(false).requireEnabled()).isInstanceOf(NotFoundException.class);
        when(store.update(anyLong(), anyLong(), anyString(), anyString(), anyInt(), any()))
                .thenThrow(new DataAccessResourceFailureException("内部配置"));
        assertThatThrownBy(() -> service.update(1, 2, UUID.randomUUID().toString(),
                new NotePresenceRequest(NotePresenceState.VIEWING, 1)))
                .isInstanceOf(NotePresenceException.class).hasMessage("在线状态暂不可用，笔记仍可正常保存");
    }

    @Test
    void machineMissingNoteAndMalformedCommandsCannotCreateLeases() {
        user();
        assertThatThrownBy(() -> service.update(1, 2, "bad", new NotePresenceRequest(NotePresenceState.VIEWING, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(1, 2, UUID.randomUUID().toString(), new NotePresenceRequest(null, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(1, 2, UUID.randomUUID().toString(), new NotePresenceRequest(NotePresenceState.VIEWING, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        when(notes.findVersionById(1L, 2L)).thenReturn(null);
        assertThatThrownBy(() -> service.get(1, 2)).isInstanceOf(NotFoundException.class);
        when(principals.require()).thenReturn(new CurrentPrincipal("machine", PrincipalType.SERVICE, "", null, Set.of()));
        assertThatThrownBy(() -> service.get(1, 2)).isInstanceOf(WorkspaceAccessDeniedException.class);
        verifyNoInteractions(store);
    }
}
