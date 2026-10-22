package com.mike.workstation.service;

import com.mike.workstation.config.NoteImportProperties;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteImportDuplicateStrategy;
import com.mike.workstation.domain.NoteRevisionSource;
import com.mike.workstation.domain.WorkspacePermission;
import com.mike.workstation.dto.NoteImportItemResponse;
import com.mike.workstation.dto.NoteImportRequest;
import com.mike.workstation.dto.NoteImportResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.NoteFolderMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Markdown 笔记导入服务。
 *
 * <p>负责从本机允许目录读取 Markdown 文件，并转成系统内的笔记记录。
 */
@Service
public class NoteImportService {
    private static final String STATUS_IMPORTED = "IMPORTED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_FAILED = "FAILED";

    private final NoteImportProperties properties;
    private final NoteMapper noteMapper;
    private final NoteFolderMapper folderMapper;
    private final ProjectMapper projectMapper;
    private final TagMapper tagMapper;
    private final WorkspaceAuthorizationService authorization;
    private final AuditService auditService;
    private final NoteRevisionService revisions;

    public NoteImportService(
            NoteImportProperties properties,
            NoteMapper noteMapper,
            NoteFolderMapper folderMapper,
            ProjectMapper projectMapper,
            TagMapper tagMapper,
            WorkspaceAuthorizationService authorization,
            AuditService auditService,
            NoteRevisionService revisions) {
        this.properties = properties;
        this.noteMapper = noteMapper;
        this.folderMapper = folderMapper;
        this.projectMapper = projectMapper;
        this.tagMapper = tagMapper;
        this.authorization = authorization;
        this.auditService = auditService;
        this.revisions = revisions;
    }

    /**
     * 执行 Markdown 导入。
     *
     * <p>支持传入单个 Markdown 文件或目录；目录会递归扫描全部 Markdown 文件。
     *
     * @param request 导入请求
     * @return 导入汇总和单文件明细
     */
    @Transactional
    public NoteImportResponse importMarkdown(Long workspaceId, NoteImportRequest request) {
        authorization.require(workspaceId, WorkspacePermission.NOTE_IMPORT);
        validateRequest(workspaceId, request);
        Path sourcePath = resolveAllowedPath(request.sourcePath());
        List<Path> markdownFiles = collectMarkdownFiles(sourcePath);
        List<Long> tagIds = normalizedTagIds(request.tagIds());
        List<NoteImportItemResponse> items =
                markdownFiles.stream()
                        .map(markdownFile -> importOne(workspaceId, markdownFile, request, tagIds))
                        .toList();

        int importedCount = count(items, STATUS_IMPORTED);
        int skippedCount = count(items, STATUS_SKIPPED);
        int failedCount = count(items, STATUS_FAILED);
        auditService.success(workspaceId, "NOTE_IMPORT", "NOTE", null);
        return new NoteImportResponse(importedCount, skippedCount, failedCount, items);
    }

    /**
     * 导入单个 Markdown 文件。
     *
     * <p>单个文件导入失败不会中断整批导入，而是以 FAILED 明细返回给前端。
     */
    private NoteImportItemResponse importOne(
            Long workspaceId, Path markdownFile, NoteImportRequest request, List<Long> tagIds) {
        String filePath = markdownFile.toString();
        Long insertedNoteId = null;
        try {
            // 单个文件过大时跳过该文件，避免一次导入把数据库或接口响应拖得过重。
            if (Files.size(markdownFile) > properties.maxFileBytes()) {
                return new NoteImportItemResponse(
                        filePath, null, STATUS_FAILED, "文件超过导入大小限制", null);
            }

            String content = Files.readString(markdownFile, StandardCharsets.UTF_8);
            String title = MarkdownTitleExtractor.extract(content, markdownFile);
            if (request.duplicateStrategy() == NoteImportDuplicateStrategy.SKIP_TITLE
                    && noteMapper.countByTitle(workspaceId, title) > 0) {
                return new NoteImportItemResponse(
                        filePath, title, STATUS_SKIPPED, "已存在同标题笔记", null);
            }

            Note note = new Note();
            note.setWorkspaceId(workspaceId);
            note.setTitle(title);
            note.setContent(content);
            note.setFolderId(request.folderId());
            note.setProjectId(request.projectId());
            LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
            note.setCreatedAt(now);
            note.setUpdatedAt(now);
            noteMapper.insert(note);
            insertedNoteId = note.getId();

            // 笔记与标签是多对多关系，创建笔记后再批量补关联。
            for (Long tagId : tagIds) {
                tagMapper.insertNoteTag(workspaceId, note.getId(), tagId);
            }
            note.setVersion(1L);
            revisions.record(note, NoteRevisionSource.IMPORT, null);

            return new NoteImportItemResponse(
                    filePath, title, STATUS_IMPORTED, "导入成功", note.getId());
        } catch (IOException | RuntimeException ex) {
            // importMarkdown 是一个事务；这里清理半成品后继续处理后续文件，让批量导入能返回完整结果。
            if (insertedNoteId != null) {
                tagMapper.deleteNoteTags(workspaceId, insertedNoteId);
                noteMapper.deleteIncompleteImport(workspaceId, insertedNoteId);
            }
            return new NoteImportItemResponse(filePath, null, STATUS_FAILED, ex.getMessage(), null);
        }
    }

    /**
     * 校验导入请求。
     *
     * <p>除基础必填项外，还会确认文件夹、项目和标签 ID 是否真实存在。
     */
    private void validateRequest(Long workspaceId, NoteImportRequest request) {
        RequestChecks.requiredObject(request, "导入请求");
        RequestChecks.requiredText(request.sourcePath(), "导入路径");
        RequestChecks.requiredObject(request.duplicateStrategy(), "重复标题处理方式");
        if (properties.allowedRoots() == null || properties.allowedRoots().isEmpty()) {
            throw new BusinessException("笔记导入允许目录未配置");
        }
        if (properties.maxFileBytes() == null || properties.maxFileBytes() < 1) {
            throw new BusinessException("笔记导入文件大小限制未正确配置");
        }
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

    /**
     * 解析并校验导入路径。
     *
     * <p>路径会被转为绝对规范路径，再与允许根目录做 startsWith 判断。
     */
    private Path resolveAllowedPath(String sourcePath) {
        Path resolvedPath = Path.of(sourcePath).toAbsolutePath().normalize();
        // 页面传入的是本机路径，必须限制在配置白名单内，避免把任意系统目录暴露给导入接口。
        boolean allowed =
                properties.allowedRoots().stream()
                        .map(root -> Path.of(root).toAbsolutePath().normalize())
                        .anyMatch(resolvedPath::startsWith);
        if (!allowed) {
            throw new BusinessException("导入路径不在允许目录内");
        }
        if (!Files.exists(resolvedPath)) {
            throw new BusinessException("导入路径不存在");
        }
        return resolvedPath;
    }

    /**
     * 收集待导入的 Markdown 文件。
     *
     * <p>传入文件时只处理该文件；传入目录时递归扫描 .md 和 .markdown 文件。
     */
    private List<Path> collectMarkdownFiles(Path sourcePath) {
        try {
            if (Files.isRegularFile(sourcePath)) {
                return isMarkdown(sourcePath) ? List.of(sourcePath) : List.of();
            }

            try (var stream = Files.walk(sourcePath)) {
                return stream.filter(Files::isRegularFile)
                        .filter(this::isMarkdown)
                        .sorted()
                        .toList();
            }
        } catch (IOException ex) {
            throw new BusinessException("读取导入路径失败");
        }
    }

    /** 判断文件是否是支持导入的 Markdown 文件。 */
    private boolean isMarkdown(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".md") || fileName.endsWith(".markdown");
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

    /** 统计指定状态的导入明细数量。 */
    private int count(List<NoteImportItemResponse> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.status())).count();
    }
}
