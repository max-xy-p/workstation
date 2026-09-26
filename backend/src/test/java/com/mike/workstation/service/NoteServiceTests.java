package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.dto.NoteDetailResponse;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.dto.NoteUpdateRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NoteVersionConflictException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;
import com.mike.workstation.mapper.TaskMapper;
import com.mike.workstation.security.CurrentPrincipal;
import com.mike.workstation.security.CurrentPrincipalResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteServiceTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock
    private NoteMapper noteMapper;
    @Mock
    private NoteFolderMapper folderMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private NoteTagLoader noteTagLoader;
    @Mock
    private CurrentPrincipalResolver principalResolver;
    @Mock
    private WorkspaceAuthorizationService authorization;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private NoteService noteService;

    @Test
    void updateReturnsThePersistedVersionReceipt() {
        Note existing = note(1L, 3L);
        Note persisted = note(1L, 4L);
        when(noteMapper.findDetailById(WORKSPACE_ID, 1L)).thenReturn(existing, persisted);
        when(tagMapper.findByNoteId(WORKSPACE_ID, 1L)).thenReturn(List.of());
        when(noteMapper.update(org.mockito.ArgumentMatchers.any(Note.class))).thenReturn(1);

        NoteDetailResponse result = noteService.update(
                WORKSPACE_ID,
                1L,
                new NoteUpdateRequest("Title", "Content", null, null, List.of(), 3L)
        );

        assertThat(result.version()).isEqualTo(4L);
    }

    @Test
    void staleUpdateDoesNotReplaceTags() {
        when(noteMapper.findDetailById(WORKSPACE_ID, 1L)).thenReturn(note(1L, 4L));
        when(noteMapper.lockById(WORKSPACE_ID, 1L)).thenReturn(note(1L, 4L));
        when(tagMapper.findByNoteId(WORKSPACE_ID, 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> noteService.update(WORKSPACE_ID, 1L,
                new NoteUpdateRequest("Title", "Content", null, null, List.of(), 3L)))
                .isInstanceOf(NoteVersionConflictException.class);
        org.mockito.Mockito.verify(tagMapper, org.mockito.Mockito.never())
                .deleteNoteTags(WORKSPACE_ID, 1L);
        org.mockito.Mockito.verifyNoInteractions(taskMapper);
    }

    @Test
    void successfulUpdateClearsEvidenceOutsideNewProject() {
        Note existing = note(1L, 3L);
        Note persisted = note(1L, 4L);
        when(noteMapper.findDetailById(WORKSPACE_ID, 1L)).thenReturn(existing, persisted);
        when(projectMapper.findById(WORKSPACE_ID, 5L)).thenReturn(new Project());
        when(tagMapper.findByNoteId(WORKSPACE_ID, 1L)).thenReturn(List.of());
        when(noteMapper.update(org.mockito.ArgumentMatchers.any(Note.class))).thenReturn(1);

        noteService.update(WORKSPACE_ID, 1L,
                new NoteUpdateRequest("Title", "Content", null, 5L, List.of(), 3L));

        verify(taskMapper).clearEvidenceOutsideProject(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRejectsTitleLongerThanDatabaseColumn() {
        assertThatThrownBy(() -> noteService.create(WORKSPACE_ID,
                new NoteRequest("x".repeat(201), "Content", null, null, List.of())
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }

    @Test
    void listReturnsPageMetadataAndBatchLoadsTags() {
        Note item = note(7L, 1L);
        when(noteMapper.listSummariesPage(WORKSPACE_ID, "spring", null, null, null, 20L, 20))
                .thenReturn(List.of(item));
        when(noteMapper.count(WORKSPACE_ID, "spring", null, null, null)).thenReturn(42L);

        var result = noteService.list(WORKSPACE_ID, "spring", null, null, null, 1, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(42L);
        assertThat(result.hasMore()).isTrue();
        verify(noteTagLoader).attachTags(WORKSPACE_ID, List.of(item));
    }

    @Test
    void deleteMovesNoteToTrashWithoutDeletingTags() {
        when(noteMapper.lockById(WORKSPACE_ID, 1L)).thenReturn(note(1L, 3L));
        when(principalResolver.require()).thenReturn(new CurrentPrincipal(
                "subject-1", PrincipalType.USER, "mike", "Mike", Set.of("WORKSTATION_USER")));

        noteService.delete(WORKSPACE_ID, 1L);

        var captor = org.mockito.ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).softDelete(captor.capture());
        assertThat(captor.getValue().getDeletedBySubject()).isEqualTo("subject-1");
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        verify(taskMapper).clearEvidenceNote(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        org.mockito.Mockito.verify(tagMapper, org.mockito.Mockito.never()).deleteNoteTags(WORKSPACE_ID, 1L);
        verify(auditService).success(WORKSPACE_ID, "NOTE_TRASH", "NOTE", 1L);
    }

    @Test
    void restoreReturnsNewActiveVersion() {
        Note deleted = note(1L, 3L);
        deleted.setDeletedAt(LocalDateTime.now());
        Note restored = note(1L, 4L);
        when(noteMapper.lockDeletedById(WORKSPACE_ID, 1L)).thenReturn(deleted);
        when(noteMapper.findDetailById(WORKSPACE_ID, 1L)).thenReturn(restored);
        when(tagMapper.findByNoteId(WORKSPACE_ID, 1L)).thenReturn(List.of());

        var result = noteService.restore(WORKSPACE_ID, 1L);

        assertThat(result.version()).isEqualTo(4L);
        verify(noteMapper).restore(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(auditService).success(WORKSPACE_ID, "NOTE_RESTORE", "NOTE", 1L);
    }

    @Test
    void permanentDeleteRemovesTagsBeforeDeletedNote() {
        Note deleted = note(1L, 3L);
        deleted.setDeletedAt(LocalDateTime.now());
        when(noteMapper.lockDeletedById(WORKSPACE_ID, 1L)).thenReturn(deleted);

        noteService.permanentlyDelete(WORKSPACE_ID, 1L);

        var order = org.mockito.Mockito.inOrder(tagMapper, noteMapper);
        order.verify(tagMapper).deleteNoteTags(WORKSPACE_ID, 1L);
        order.verify(noteMapper).permanentlyDelete(WORKSPACE_ID, 1L);
        verify(auditService).success(WORKSPACE_ID, "NOTE_PERMANENT_DELETE", "NOTE", 1L);
    }

    @Test
    void listDeletedReturnsDeletionMetadataAndPageState() {
        Note deleted = note(7L, 2L);
        deleted.setDeletedAt(LocalDateTime.now());
        deleted.setDeletedBySubject("subject-1");
        deleted.setDeletedByDisplayName("Mike");
        when(noteMapper.listDeletedPage(WORKSPACE_ID, "旧", 0L, 20)).thenReturn(List.of(deleted));
        when(noteMapper.countDeleted(WORKSPACE_ID, "旧")).thenReturn(1L);

        var result = noteService.listDeleted(WORKSPACE_ID, "旧", 0, 20);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.deletedBySubject()).isEqualTo("subject-1");
            assertThat(item.deletedByDisplayName()).isEqualTo("Mike");
        });
        verify(noteTagLoader).attachTags(WORKSPACE_ID, List.of(deleted));
    }

    private Note note(Long id, Long version) {
        Note note = new Note();
        note.setId(id);
        note.setTitle("Title");
        note.setContent("Content");
        note.setVersion(version);
        return note;
    }
}
