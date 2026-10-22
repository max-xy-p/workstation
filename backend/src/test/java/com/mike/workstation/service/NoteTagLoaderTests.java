package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteTagRow;
import com.mike.workstation.mapper.TagMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class NoteTagLoaderTests {
    private static final long WORKSPACE_ID = 9L;

    @Mock private TagMapper tagMapper;
    @InjectMocks private NoteTagLoader noteTagLoader;

    @Test
    void loadsTagsForAllNotesWithOneMapperCall() {
        Note first = note(1L);
        Note second = note(2L);
        when(tagMapper.findByNoteIds(WORKSPACE_ID, List.of(1L, 2L)))
                .thenReturn(List.of(row(1L, 10L, "Java")));

        noteTagLoader.attachTags(WORKSPACE_ID, List.of(first, second));

        verify(tagMapper).findByNoteIds(WORKSPACE_ID, List.of(1L, 2L));
        assertThat(first.getTags()).extracting(tag -> tag.getName()).containsExactly("Java");
        assertThat(second.getTags()).isEmpty();
    }

    private Note note(Long id) {
        Note note = new Note();
        note.setId(id);
        return note;
    }

    private NoteTagRow row(Long noteId, Long tagId, String name) {
        NoteTagRow row = new NoteTagRow();
        row.setNoteId(noteId);
        row.setId(tagId);
        row.setName(name);
        return row;
    }
}
