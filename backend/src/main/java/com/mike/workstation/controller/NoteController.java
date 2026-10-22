package com.mike.workstation.controller;

import com.mike.workstation.dto.DeletedNoteResponse;
import com.mike.workstation.dto.NoteDetailResponse;
import com.mike.workstation.dto.NoteImportRequest;
import com.mike.workstation.dto.NoteImportResponse;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.dto.NoteRevisionResponse;
import com.mike.workstation.dto.NoteRevisionRestoreRequest;
import com.mike.workstation.dto.NoteRevisionSummaryResponse;
import com.mike.workstation.dto.NoteSummaryResponse;
import com.mike.workstation.dto.NoteUpdateRequest;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.service.NoteImportService;
import com.mike.workstation.service.NoteRevisionService;
import com.mike.workstation.service.NoteService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记接口控制器。
 *
 * <p>提供 Markdown 笔记的列表筛选、详情、创建、编辑、删除和本地 Markdown 导入接口。
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/notes")
public class NoteController {
    private final NoteService noteService;
    private final NoteImportService noteImportService;
    private final NoteRevisionService revisions;

    public NoteController(
            NoteService noteService,
            NoteImportService noteImportService,
            NoteRevisionService revisions) {
        this.noteService = noteService;
        this.noteImportService = noteImportService;
        this.revisions = revisions;
    }

    @GetMapping("/{id}/revisions")
    public PageResponse<NoteRevisionSummaryResponse> revisions(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestParam(required = false) Long upToVersion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return revisions.list(workspaceId, id, upToVersion, page, size);
    }

    @GetMapping("/{id}/revisions/{revisionId}")
    public NoteRevisionResponse revision(
            @PathVariable Long workspaceId, @PathVariable Long id, @PathVariable Long revisionId) {
        return revisions.get(workspaceId, id, revisionId);
    }

    @PostMapping("/{id}/revisions/{revisionId}/restore")
    public NoteDetailResponse restoreRevision(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @PathVariable Long revisionId,
            @RequestBody NoteRevisionRestoreRequest request) {
        return noteService.restoreRevision(workspaceId, id, revisionId, request);
    }

    /**
     * 查询笔记列表。
     *
     * @param keyword 笔记标题或正文关键字，可为空
     * @param folderId 文件夹筛选，可为空
     * @param tagId 标签筛选，可为空
     * @param projectId 关联项目筛选，可为空
     * @return 满足筛选条件的笔记列表，每条笔记会携带标签信息
     */
    @GetMapping
    public PageResponse<NoteSummaryResponse> list(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noteService.list(workspaceId, keyword, folderId, tagId, projectId, page, size);
    }

    /** 查询回收站中的笔记。 */
    @GetMapping("/trash")
    public PageResponse<DeletedNoteResponse> listDeleted(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noteService.listDeleted(workspaceId, keyword, page, size);
    }

    /**
     * 查询单篇笔记详情。
     *
     * @param id 笔记 ID
     * @return 笔记详情，包含文件夹、项目和标签信息
     */
    @GetMapping("/{id}")
    public NoteDetailResponse get(@PathVariable Long workspaceId, @PathVariable Long id) {
        return noteService.get(workspaceId, id);
    }

    /**
     * 创建 Markdown 笔记。
     *
     * @param request 笔记创建请求，标题和正文必填
     * @return 创建后的笔记数据
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDetailResponse create(
            @PathVariable Long workspaceId, @RequestBody NoteRequest request) {
        return noteService.create(workspaceId, request);
    }

    /**
     * 从本机路径导入 Markdown 笔记。
     *
     * <p>导入路径必须位于配置允许的根目录下，避免接口读取任意系统文件。
     *
     * @param request 导入请求，包含源路径、文件夹、项目、标签和重复标题处理策略
     * @return 导入结果汇总和每个文件的处理明细
     */
    @PostMapping("/import")
    public NoteImportResponse importMarkdown(
            @PathVariable Long workspaceId, @RequestBody NoteImportRequest request) {
        return noteImportService.importMarkdown(workspaceId, request);
    }

    /**
     * 更新笔记完整信息。
     *
     * @param id 笔记 ID
     * @param request 笔记更新请求
     * @return 更新后的笔记数据
     */
    @PutMapping("/{id}")
    public NoteDetailResponse update(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestBody NoteUpdateRequest request) {
        return noteService.update(workspaceId, id, request);
    }

    /**
     * 将笔记移入回收站。
     *
     * @param id 笔记 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long id) {
        noteService.delete(workspaceId, id);
    }

    /** 从回收站恢复笔记。 */
    @PostMapping("/{id}/restore")
    public NoteDetailResponse restore(@PathVariable Long workspaceId, @PathVariable Long id) {
        return noteService.restore(workspaceId, id);
    }

    /** 永久删除回收站中的笔记。 */
    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDelete(@PathVariable Long workspaceId, @PathVariable Long id) {
        noteService.permanentlyDelete(workspaceId, id);
    }
}
