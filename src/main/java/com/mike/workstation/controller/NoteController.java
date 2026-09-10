package com.mike.workstation.controller;

import com.mike.workstation.domain.Note;
import com.mike.workstation.dto.NoteImportRequest;
import com.mike.workstation.dto.NoteImportResponse;
import com.mike.workstation.dto.NoteRequest;
import com.mike.workstation.service.NoteImportService;
import com.mike.workstation.service.NoteService;
import java.util.List;
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

@RestController
@RequestMapping("/api/notes")
/**
 * 笔记接口控制器。
 *
 * <p>提供 Markdown 笔记的列表筛选、详情、创建、编辑、删除和本地 Markdown 导入接口。</p>
 */
public class NoteController {
    private final NoteService noteService;
    private final NoteImportService noteImportService;

    public NoteController(NoteService noteService, NoteImportService noteImportService) {
        this.noteService = noteService;
        this.noteImportService = noteImportService;
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
    public List<Note> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long projectId
    ) {
        return noteService.list(keyword, folderId, tagId, projectId);
    }

    /**
     * 查询单篇笔记详情。
     *
     * @param id 笔记 ID
     * @return 笔记详情，包含文件夹、项目和标签信息
     */
    @GetMapping("/{id}")
    public Note get(@PathVariable Long id) {
        return noteService.get(id);
    }

    /**
     * 创建 Markdown 笔记。
     *
     * @param request 笔记创建请求，标题和正文必填
     * @return 创建后的笔记数据
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Note create(@RequestBody NoteRequest request) {
        return noteService.create(request);
    }

    /**
     * 从本机路径导入 Markdown 笔记。
     *
     * <p>导入路径必须位于配置允许的根目录下，避免接口读取任意系统文件。</p>
     *
     * @param request 导入请求，包含源路径、文件夹、项目、标签和重复标题处理策略
     * @return 导入结果汇总和每个文件的处理明细
     */
    @PostMapping("/import")
    public NoteImportResponse importMarkdown(@RequestBody NoteImportRequest request) {
        return noteImportService.importMarkdown(request);
    }

    /**
     * 更新笔记完整信息。
     *
     * @param id 笔记 ID
     * @param request 笔记更新请求
     * @return 更新后的笔记数据
     */
    @PutMapping("/{id}")
    public Note update(@PathVariable Long id, @RequestBody NoteRequest request) {
        return noteService.update(id, request);
    }

    /**
     * 删除笔记。
     *
     * <p>删除笔记会先移除笔记与标签的关联，不影响项目、文件夹、标签本身。</p>
     *
     * @param id 笔记 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        noteService.delete(id);
    }
}
