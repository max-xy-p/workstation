package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.mike.workstation.domain.Note;
import com.mike.workstation.dto.NoteDetailResponse;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;
import java.util.List;
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
    private NoteTagLoader noteTagLoader;
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

        NoteDetailResponse result = noteService.update(
                WORKSPACE_ID,
                1L,
                new NoteRequest("Title", "Content", null, null, List.of())
        );

        assertThat(result.version()).isEqualTo(4L);
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

    private Note note(Long id, Long version) {
        Note note = new Note();
        note.setId(id);
        note.setTitle("Title");
        note.setContent("Content");
        note.setVersion(version);
        return note;
    }
}
