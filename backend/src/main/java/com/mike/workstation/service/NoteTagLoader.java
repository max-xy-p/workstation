package com.mike.workstation.service;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteTagRow;
import com.mike.workstation.domain.Tag;
import com.mike.workstation.mapper.TagMapper;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NoteTagLoader {
    private final TagMapper tagMapper;

    public NoteTagLoader(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public void attachTags(Long workspaceId, List<Note> notes) {
        if (notes.isEmpty()) {
            return;
        }
        List<Long> noteIds = notes.stream().map(Note::getId).toList();
        Map<Long, List<Tag>> tagsByNoteId =
                tagMapper.findByNoteIds(workspaceId, noteIds).stream()
                        .collect(
                                Collectors.groupingBy(
                                        NoteTagRow::getNoteId,
                                        Collectors.mapping(this::toTag, Collectors.toList())));
        notes.forEach(note -> note.setTags(tagsByNoteId.getOrDefault(note.getId(), List.of())));
    }

    private Tag toTag(NoteTagRow row) {
        Tag tag = new Tag();
        tag.setId(row.getId());
        tag.setName(row.getName());
        tag.setCreatedAt(row.getCreatedAt());
        tag.setUpdatedAt(row.getUpdatedAt());
        return tag;
    }
}
