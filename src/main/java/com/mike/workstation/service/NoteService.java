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
/**
 * 笔记业务服务。
 *
 * <p>负责 Markdown 笔记的 CRUD、分类/项目存在性校验，以及笔记标签多对多关系维护。</p>
 */
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

    /**
     * 查询笔记列表，并为每篇笔记补充标签集合。
     *
     * @param keyword 标题关键字，可为空
     * @param categoryId 分类筛选，可为空
     * @param tagId 标签筛选，可为空
     * @param projectId 关联项目筛选，可为空
     * @return 笔记列表
     */
    public List<Note> list(String keyword, Long categoryId, Long tagId, Long projectId) {
        List<Note> notes = noteMapper.list(keyword, categoryId, tagId, projectId);
        notes.forEach(this::attachTags);
        return notes;
    }

    /**
     * 查询单篇笔记详情。
     *
     * @param id 笔记 ID
     * @return 笔记详情
     */
    public Note get(Long id) {
        Note note = noteMapper.findById(id);
        RequestChecks.requiredExisting(note, "笔记不存在");
        attachTags(note);
        return note;
    }

    /**
     * 创建笔记并写入标签关联。
     *
     * @param request 笔记创建请求
     * @return 创建后的笔记
     */
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

    /**
     * 更新笔记正文、分类、项目和标签关联。
     *
     * @param id 笔记 ID
     * @param request 笔记更新请求
     * @return 更新后的笔记
     */
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

    /**
     * 删除笔记。
     *
     * <p>先删除 note_tag 关联，再删除笔记正文记录。</p>
     *
     * @param id 笔记 ID
     */
    @Transactional
    public void delete(Long id) {
        get(id);
        tagMapper.deleteNoteTags(id);
        noteMapper.delete(id);
    }

    /**
     * 校验笔记请求。
     *
     * <p>分类、项目和标签都是可选关系；只要传入 ID，就必须能在数据库中找到对应记录。</p>
     */
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

    /**
     * 把请求中的基础字段复制到笔记实体。
     */
    private void applyRequest(Note note, NoteRequest request) {
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setCategoryId(request.categoryId());
        note.setProjectId(request.projectId());
    }

    /**
     * 给笔记补充标签列表。
     */
    private void attachTags(Note note) {
        note.setTags(tagMapper.findByNoteId(note.getId()));
    }

    /**
     * 替换笔记的全部标签。
     *
     * <p>第一版不做局部增删标签，统一先删再插，逻辑简单且便于保证最终状态与请求一致。</p>
     */
    private void replaceTags(Long noteId, List<Long> tagIds) {
        tagMapper.deleteNoteTags(noteId);
        for (Long tagId : normalizedTagIds(tagIds)) {
            tagMapper.insertNoteTag(noteId, tagId);
        }
    }

    /**
     * 清洗标签 ID 列表。
     *
     * <p>过滤空值并去重，避免重复插入 note_tag 主键。</p>
     */
    private List<Long> normalizedTagIds(List<Long> tagIds) {
        if (tagIds == null) {
            return List.of();
        }
        return tagIds.stream().filter(id -> id != null).distinct().toList();
    }
}
