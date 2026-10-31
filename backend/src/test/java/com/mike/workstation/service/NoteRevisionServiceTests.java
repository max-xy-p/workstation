package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteRevision;
import com.mike.workstation.domain.NoteRevisionSource;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.NoteRevisionMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

class NoteRevisionServiceTests {
    private final NoteRevisionMapper mapper = mock(NoteRevisionMapper.class);
    private final NoteMapper notes = mock(NoteMapper.class);
    private final WorkspaceAuthorizationService authorization =
            mock(WorkspaceAuthorizationService.class);
    private final CurrentPrincipalResolver principals = mock(CurrentPrincipalResolver.class);
    private final NoteRevisionService service =
            new NoteRevisionService(mapper, notes, authorization, principals);

    private NoteRevision revision(String content) {
        NoteRevision r = new NoteRevision();
        r.setId(11L);
        r.setNoteId(3L);
        r.setNoteVersion(5L);
        r.setTitle("标题");
        r.setContent(content);
        r.setCreatedAt(LocalDateTime.of(2026, 10, 19, 12, 0));
        r.setSource(NoteRevisionSource.UPDATE);
        return r;
    }

    private Note note(String content) {
        Note n = new Note();
        n.setId(3L);
        n.setWorkspaceId(9L);
        n.setVersion(7L);
        n.setTitle("标题");
        n.setContent(content);
        return n;
    }

    private void actor() {
        when(principals.require())
                .thenReturn(
                        new CurrentPrincipal("author", PrincipalType.USER, "作者", null, Set.of()));
    }

    @Test
    void listPinsPaginationToRequestedVersionAndReturnsOnlySummary() {
        when(notes.findVersionById(9L, 3L)).thenReturn(8L);
        when(mapper.list(9L, 3L, 5L, 20L, 20)).thenReturn(List.of(revision("正文")));
        when(mapper.count(9L, 3L, 5L)).thenReturn(21L);
        var result = service.list(9L, 3L, 5L, 1, 20);
        assertThat(result.total()).isEqualTo(21);
        assertThat(result.items())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.noteVersion()).isEqualTo(5L);
                            assertThat(item.createdAt()).isNotNull();
                        });
        assertThat(result.hasMore()).isFalse();
        verify(authorization).require(9L, WorkspacePermission.NOTE_READ);
    }

    @Test
    void missingUpperBoundUsesCurrentVersionAndFutureUpperBoundIsClamped() {
        when(notes.findVersionById(9L, 3L)).thenReturn(8L);
        when(mapper.list(9L, 3L, 8L, 0L, 20)).thenReturn(List.of());
        service.list(9L, 3L, null, 0, 20);
        service.list(9L, 3L, 99L, 0, 20);
        verify(mapper, times(2)).list(9L, 3L, 8L, 0L, 20);
    }

    @Test
    void rejectsInvalidPaginationUpperBoundAndMissingActiveNote() {
        when(notes.findVersionById(9L, 3L)).thenReturn(null);
        assertThatThrownBy(() -> service.list(9L, 3L, 0L, 0, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(9L, 3L, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(9L, 3L, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(9L, 3L, null, 0, 20))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void detailReturnsFullSnapshotAndPreservesUnknownBaselineAuthor() {
        NoteRevision r = revision("历史正文");
        r.setSource(NoteRevisionSource.BASELINE);
        when(mapper.find(9L, 3L, 11L)).thenReturn(r);
        var result = service.get(9L, 3L, 11L);
        assertThat(result.content()).isEqualTo("历史正文");
        assertThat(result.actorSubject()).isNull();
        assertThat(result.source()).isEqualTo(NoteRevisionSource.BASELINE);
    }

    @Test
    void missingOrForeignRevisionDoesNotRevealContent() {
        assertThatThrownBy(() -> service.get(9L, 3L, 11L)).isInstanceOf(NotFoundException.class);
        verify(mapper).find(9L, 3L, 11L);
    }

    @Test
    void permissionRejectionHappensBeforeReadingHistory() {
        doThrow(new WorkspaceAccessDeniedException("无读取权限"))
                .when(authorization)
                .require(9L, WorkspacePermission.NOTE_READ);
        assertThatThrownBy(() -> service.get(9L, 3L, 11L))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        verifyNoInteractions(mapper, notes);
    }

    @Test
    void unchangedContentDoesNotDuplicateSnapshot() {
        when(mapper.latestForUpdate(9L, 3L)).thenReturn(revision("A"));
        service.recordSnapshot(note("A"), NoteRevisionSource.UPDATE, null);
        verify(mapper, never()).insert(any());
        verifyNoInteractions(principals);
    }

    @Test
    void returningToEarlierContentStillCreatesSnapshot() {
        actor();
        when(mapper.latestForUpdate(9L, 3L)).thenReturn(revision("B"));
        service.recordSnapshot(note("A"), NoteRevisionSource.UPDATE, null);
        ArgumentCaptor<NoteRevision> captured = ArgumentCaptor.forClass(NoteRevision.class);
        verify(mapper).insert(captured.capture());
        assertThat(captured.getValue().getContent()).isEqualTo("A");
        assertThat(captured.getValue().getNoteVersion()).isEqualTo(7L);
        assertThat(captured.getValue().getActorSubject()).isEqualTo("author");
    }

    @Test
    void createImportAndExplicitRestoreAlwaysRecordServerOrigin() {
        actor();
        service.recordSnapshot(note("A"), NoteRevisionSource.CREATE, null);
        service.recordSnapshot(note("A"), NoteRevisionSource.IMPORT, null);
        service.recordSnapshot(note("A"), NoteRevisionSource.RESTORE, 11L);
        ArgumentCaptor<NoteRevision> captured = ArgumentCaptor.forClass(NoteRevision.class);
        verify(mapper, times(3)).insert(captured.capture());
        assertThat(captured.getAllValues())
                .extracting(NoteRevision::getSource)
                .containsExactly(
                        NoteRevisionSource.CREATE,
                        NoteRevisionSource.IMPORT,
                        NoteRevisionSource.RESTORE);
        assertThat(captured.getAllValues().get(2).getRestoredFromRevisionId()).isEqualTo(11L);
        verify(mapper, never()).latestForUpdate(anyLong(), anyLong());
    }

    @Test
    void updateWithoutAnyHistoryCreatesFirstSnapshot() {
        actor();
        service.recordSnapshot(note("首次快照"), NoteRevisionSource.UPDATE, null);
        verify(mapper).insert(any(NoteRevision.class));
    }

    @Test
    void titleOnlyChangeStillCreatesSnapshot() {
        actor();
        NoteRevision previous = revision("A");
        previous.setTitle("旧标题");
        when(mapper.latestForUpdate(9L, 3L)).thenReturn(previous);
        service.recordSnapshot(note("A"), NoteRevisionSource.UPDATE, null);
        verify(mapper).insert(any(NoteRevision.class));
    }
}
