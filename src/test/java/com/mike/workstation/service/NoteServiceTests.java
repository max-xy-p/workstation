package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Note;
import com.mike.workstation.dto.NoteRequest;
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
    @Mock
    private NoteMapper noteMapper;
    @Mock
    private NoteFolderMapper folderMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TagMapper tagMapper;
    @InjectMocks
    private NoteService noteService;

    @Test
    void updateReturnsThePersistedVersionReceipt() {
        Note existing = note(1L, 3L);
        Note persisted = note(1L, 4L);
        when(noteMapper.findById(1L)).thenReturn(existing, persisted);
        when(tagMapper.findByNoteId(1L)).thenReturn(List.of());

        Note result = noteService.update(
                1L,
                new NoteRequest("Title", "Content", null, null, List.of())
        );

        assertThat(result.getVersion()).isEqualTo(4L);
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
