package com.mike.workstation.service;

import com.mike.workstation.assembler.NoteResponseAssembler;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteRevision;
import com.mike.workstation.domain.NoteRevisionSource;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.DeletedNoteResponse;
import com.mike.workstation.dto.NoteDetailResponse;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.dto.NoteRevisionRestoreRequest;
import com.mike.workstation.dto.NoteSummaryResponse;
import com.mike.workstation.dto.NoteUpdateRequest;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NoteVersionConflictException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;
import com.mike.workstation.mapper.TaskMapper;
import com.mike.workstation.security.CurrentPrincipalResolver;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 笔记业务服务。
 *
 * <p>负责 Markdown 笔记的 CRUD、文件夹/项目存在性校验，以及笔记标签多对多关系维护。
 */
@Service
public class NoteService {
    private static final String NOTE_NOT_FOUND = "笔记不存在";
    private final NoteMapper noteMapper;
    private final NoteFolderMapper folderMapper;
    private final ProjectMapper projectMapper;
    private final TagMapper tagMapper;
    private final TaskMapper taskMapper;
    private final NoteTagLoader noteTagLoader;
    private final CurrentPrincipalResolver principalResolver;
    private final WorkspaceAuthorizationService authorization;
    private final AuditService auditService;
    private final NoteRevisionService revisions;

    public NoteService(
            NoteMapper noteMapper,
            NoteFolderMapper folderMapper,
            ProjectMapper projectMapper,
            TagMapper tagMapper,
            TaskMapper taskMapper,
            NoteTagLoader noteTagLoader,
            CurrentPrincipalResolver principalResolver,
            WorkspaceAuthorizationService authorization,
            AuditService auditService,
            NoteRevisionService revisions) {
        this.noteMapper = noteMapper;
        this.folderMapper = folderMapper;
        this.projectMapper = projectMapper;
        this.tagMapper = tagMapper;
        this.taskMapper = taskMapper;
        this.noteTagLoader = noteTagLoader;
        this.principalResolver = principalResolver;
        this.authorization = authorization;
        this.auditService = auditService;
        this.revisions = revisions;
    }

    /**
     * 查询笔记列表，并为每篇笔记补充标签集合。
     *
     * @param keyword 标题关键字，可为空
     * @param folderId 文件夹筛选，可为空
     * @param tagId 标签筛选，可为空
     * @param projectId 关联项目筛选，可为空
     * @return 笔记列表
     */
    public PageResponse<NoteSummaryResponse> list(
            Long workspaceId,
            String keyword,
            Long folderId,
            Long tagId,
            Long projectId,
            int page,
            int size) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_READ);
        PageQuery query = PageQuery.of(page, size);
        List<Note> notes =
                noteMapper.listSummariesPage(
                        workspaceId,
                        keyword,
                        folderId,
                        tagId,
                        projectId,
                        query.offset(),
                        query.size());
        noteTagLoader.attachTags(workspaceId, notes);
        long total = noteMapper.count(workspaceId, keyword, folderId, tagId, projectId);
        return PageResponse.of(
                notes.stream().map(NoteResponseAssembler::toSummary).toList(), page, size, total);
    }

    /** 查询当前工作空间的回收站。 */
    public PageResponse<DeletedNoteResponse> listDeleted(
            Long workspaceId, String keyword, int page, int size) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_READ);
        PageQuery query = PageQuery.of(page, size);
        List<Note> notes =
                noteMapper.listDeletedPage(workspaceId, keyword, query.offset(), query.size());
        noteTagLoader.attachTags(workspaceId, notes);
        long total = noteMapper.countDeleted(workspaceId, keyword);
        return PageResponse.of(
                notes.stream().map(NoteResponseAssembler::toDeleted).toList(), page, size, total);
    }

    /**
     * 查询单篇笔记详情。
     *
     * @param id 笔记 ID
     * @return 笔记详情
     */
    public NoteDetailResponse get(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_READ);
        return NoteResponseAssembler.toDetail(getEntity(workspaceId, id));
    }

    private Note getEntity(Long workspaceId, Long id) {
        Note note = noteMapper.findDetailById(workspaceId, id);
        RequestChecks.requiredExisting(note, NOTE_NOT_FOUND);
        attachTags(workspaceId, note);
        return note;
    }

    /**
     * 创建笔记并写入标签关联。
     *
     * @param request 笔记创建请求
     * @return 创建后的笔记
     */
    @Transactional
    public NoteDetailResponse create(Long workspaceId, NoteRequest request) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_WRITE);
        validate(workspaceId, request);
        Note note = new Note();
        note.setWorkspaceId(workspaceId);
        applyRequest(note, request);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        noteMapper.insert(note);
        note.setVersion(1L);
        revisions.record(note, NoteRevisionSource.CREATE, null);
        replaceTags(workspaceId, note.getId(), request.tagIds());
        auditService.success(workspaceId, "NOTE_CREATE", "NOTE", note.getId());
        return get(workspaceId, note.getId());
    }

    /**
     * 更新笔记正文、文件夹、项目和标签关联。
     *
     * @param id 笔记 ID
     * @param request 笔记更新请求
     * @return 更新后的笔记
     */
    @Transactional
    public NoteDetailResponse update(Long workspaceId, Long id, NoteUpdateRequest request) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_WRITE);
        RequestChecks.requiredObject(request, "笔记更新请求");
        if (request.expectedVersion() == null || request.expectedVersion() < 1) {
            throw new BusinessException("笔记版本不合法");
        }
        NoteRequest noteRequest = request.note();
        validate(workspaceId, noteRequest);
        getEntity(workspaceId, id);
        Note note = new Note();
        note.setId(id);
        note.setWorkspaceId(workspaceId);
        note.setVersion(request.expectedVersion());
        applyRequest(note, noteRequest);
        note.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        if (noteMapper.update(note) == 0) {
            RequestChecks.requiredExisting(noteMapper.lockById(workspaceId, id), NOTE_NOT_FOUND);
            throw new NoteVersionConflictException();
        }
        note.setVersion(request.expectedVersion() + 1);
        revisions.record(note, NoteRevisionSource.UPDATE, null);
        taskMapper.clearEvidenceOutsideProject(
                workspaceId, id, note.getProjectId(), note.getUpdatedAt());
        replaceTags(workspaceId, id, noteRequest.tagIds());
        auditService.success(workspaceId, "NOTE_UPDATE", "NOTE", id);
        return get(workspaceId, id);
    }

    /** 将历史标题与正文保存为新版本；并发修改仍需通过版本校验。 */
    @Transactional
    public NoteDetailResponse restoreRevision(
            Long workspaceId, Long id, Long revisionId, NoteRevisionRestoreRequest request) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_WRITE);
        RequestChecks.requiredObject(request, "历史恢复请求");
        if (request.expectedVersion() == null || request.expectedVersion() < 1) {
            throw new BusinessException("笔记版本不合法");
        }
        Note note = noteMapper.lockContentById(workspaceId, id);
        RequestChecks.requiredExisting(note, NOTE_NOT_FOUND);
        NoteRevision revision = revisions.require(workspaceId, id, revisionId);
        if (!request.expectedVersion().equals(note.getVersion())) {
            throw new NoteVersionConflictException();
        }
        note.setTitle(revision.getTitle());
        note.setContent(revision.getContent());
        note.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        if (noteMapper.restoreContent(note) != 1) {
            throw new NoteVersionConflictException();
        }
        note.setVersion(request.expectedVersion() + 1);
        revisions.record(note, NoteRevisionSource.RESTORE, revisionId);
        auditService.success(workspaceId, "NOTE_REVISION_RESTORE", "NOTE", id);
        return get(workspaceId, id);
    }

    /**
     * 将笔记移入回收站。
     *
     * @param id 笔记 ID
     */
    @Transactional
    public void delete(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_WRITE);
        RequestChecks.requiredExisting(noteMapper.lockById(workspaceId, id), NOTE_NOT_FOUND);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        taskMapper.clearEvidenceNote(workspaceId, id, now);
        Note note = new Note();
        note.setId(id);
        note.setWorkspaceId(workspaceId);
        note.setDeletedAt(now);
        note.setDeletedBySubject(principalResolver.require().subjectId());
        noteMapper.softDelete(note);
        auditService.success(workspaceId, "NOTE_TRASH", "NOTE", id);
    }

    /** 从回收站恢复笔记，并生成新的持久化版本。 */
    @Transactional
    public NoteDetailResponse restore(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_WRITE);
        RequestChecks.requiredExisting(noteMapper.lockDeletedById(workspaceId, id), "回收站中不存在该笔记");
        noteMapper.restore(workspaceId, id, LocalDateTime.now(ZoneId.systemDefault()));
        auditService.success(workspaceId, "NOTE_RESTORE", "NOTE", id);
        return get(workspaceId, id);
    }

    /** 永久删除回收站中的笔记。 */
    @Transactional
    public void permanentlyDelete(Long workspaceId, Long id) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_WRITE);
        RequestChecks.requiredExisting(noteMapper.lockDeletedById(workspaceId, id), "回收站中不存在该笔记");
        tagMapper.deleteNoteTags(workspaceId, id);
        noteMapper.permanentlyDelete(workspaceId, id);
        auditService.success(workspaceId, "NOTE_PERMANENT_DELETE", "NOTE", id);
    }

    /**
     * 校验笔记请求。
     *
     * <p>文件夹、项目和标签都是可选关系；只要传入 ID，就必须能在数据库中找到对应记录。
     */
    private void validate(Long workspaceId, NoteRequest request) {
        RequestChecks.requiredObject(request, "笔记请求");
        RequestChecks.requiredText(request.title(), "笔记标题");
        RequestChecks.requiredText(request.content(), "笔记内容");
        RequestChecks.maxLength(request.title().trim(), 200, "笔记标题");
        if (request.folderId() != null) {
            RequestChecks.requiredExisting(
                    folderMapper.findById(workspaceId, request.folderId()), "文件夹不存在");
        }
        if (request.projectId() != null) {
            RequestChecks.requiredExisting(
                    projectMapper.findById(workspaceId, request.projectId()), "关联项目不存在");
        }
        List<Long> tagIds = normalizedTagIds(request.tagIds());
        if (RequestChecks.hasItems(tagIds)
                && tagMapper.findByIds(workspaceId, tagIds).size() != tagIds.size()) {
            throw new BusinessException("存在无效标签");
        }
    }

    /** 把请求中的基础字段复制到笔记实体。 */
    private void applyRequest(Note note, NoteRequest request) {
        note.setTitle(request.title().trim());
        note.setContent(request.content());
        note.setFolderId(request.folderId());
        note.setProjectId(request.projectId());
    }

    /** 给笔记补充标签列表。 */
    private void attachTags(Long workspaceId, Note note) {
        note.setTags(tagMapper.findByNoteId(workspaceId, note.getId()));
    }

    /**
     * 替换笔记的全部标签。
     *
     * <p>第一版不做局部增删标签，统一先删再插，逻辑简单且便于保证最终状态与请求一致。
     */
    private void replaceTags(Long workspaceId, Long noteId, List<Long> tagIds) {
        tagMapper.deleteNoteTags(workspaceId, noteId);
        for (Long tagId : normalizedTagIds(tagIds)) {
            tagMapper.insertNoteTag(workspaceId, noteId, tagId);
        }
    }

    /**
     * 清洗标签 ID 列表。
     *
     * <p>过滤空值并去重，避免重复插入 note_tag 主键。
     */
    private List<Long> normalizedTagIds(List<Long> tagIds) {
        if (tagIds == null) {
            return List.of();
        }
        return tagIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
