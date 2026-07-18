package com.mike.workstation.service;

import com.mike.workstation.domain.Note;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.CategoryMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private final NoteMapper noteMapper;
    private final CategoryMapper categoryMapper;
    private final ProjectMapper projectMapper;
    private final TagMapper tagMapper;

    public NoteService(
            NoteMapper noteMapper,
            CategoryMapper categoryMapper,
            ProjectMapper projectMapper,
            TagMapper tagMapper
    ) {
        this.noteMapper = noteMapper;
        this.categoryMapper = categoryMapper;
        this.projectMapper = projectMapper;
        this.tagMapper = tagMapper;
    }

    public List<Note> list(String keyword, Long categoryId, Long tagId, Long projectId) {
        List<Note> notes = noteMapper.list(keyword, categoryId, tagId, projectId);
        notes.forEach(this::attachTags);
        return notes;
    }

    public Note get(Long id) {
        Note note = noteMapper.findById(id);
        RequestChecks.requiredExisting(note, "笔记不存在");
        attachTags(note);
        return note;
    }

    @Transactional
    public Note create(NoteRequest request) {
        validate(request);
        Note note = new Note();
        applyRequest(note, request);
        LocalDateTime now = LocalDateTime.now();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        noteMapper.insert(note);
        replaceTags(note.getId(), request.tagIds());
        return get(note.getId());
    }

    @Transactional
    public Note update(Long id, NoteRequest request) {
        validate(request);
        get(id);
        Note note = new Note();
        note.setId(id);
        applyRequest(note, request);
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.update(note);
        replaceTags(id, request.tagIds());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        tagMapper.deleteNoteTags(id);
        noteMapper.delete(id);
    }

    private void validate(NoteRequest request) {
        RequestChecks.requiredObject(request, "笔记请求");
        RequestChecks.requiredText(request.title(), "笔记标题");
        RequestChecks.requiredText(request.content(), "笔记内容");
        if (request.categoryId() != null) {
            RequestChecks.requiredExisting(categoryMapper.findById(request.categoryId()), "分类不存在");
        }
        if (request.projectId() != null) {
            RequestChecks.requiredExisting(projectMapper.findById(request.projectId()), "关联项目不存在");
        }
        List<Long> tagIds = normalizedTagIds(request.tagIds());
        if (RequestChecks.hasItems(tagIds) && tagMapper.findByIds(tagIds).size() != tagIds.size()) {
            throw new BusinessException("存在无效标签");
        }
    }

    private void applyRequest(Note note, NoteRequest request) {
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setCategoryId(request.categoryId());
        note.setProjectId(request.projectId());
    }

    private void attachTags(Note note) {
        note.setTags(tagMapper.findByNoteId(note.getId()));
    }

    private void replaceTags(Long noteId, List<Long> tagIds) {
        tagMapper.deleteNoteTags(noteId);
        for (Long tagId : normalizedTagIds(tagIds)) {
            tagMapper.insertNoteTag(noteId, tagId);
        }
    }

    private List<Long> normalizedTagIds(List<Long> tagIds) {
        if (tagIds == null) {
            return List.of();
        }
        return tagIds.stream().filter(id -> id != null).distinct().toList();
    }
}
