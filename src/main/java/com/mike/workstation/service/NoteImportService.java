package com.mike.workstation.service;

import com.mike.workstation.config.NoteImportProperties;
import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteImportDuplicateStrategy;
import com.mike.workstation.dto.NoteImportItemResponse;
import com.mike.workstation.dto.NoteImportRequest;
import com.mike.workstation.dto.NoteImportResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.mapper.CategoryMapper;
import com.mike.workstation.mapper.NoteMapper;
import com.mike.workstation.mapper.ProjectMapper;
import com.mike.workstation.mapper.TagMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteImportService {
    private static final String STATUS_IMPORTED = "IMPORTED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_FAILED = "FAILED";

    private final NoteImportProperties properties;
    private final NoteMapper noteMapper;
    private final CategoryMapper categoryMapper;
    private final ProjectMapper projectMapper;
    private final TagMapper tagMapper;

    public NoteImportService(
            NoteImportProperties properties,
            NoteMapper noteMapper,
            CategoryMapper categoryMapper,
            ProjectMapper projectMapper,
            TagMapper tagMapper
    ) {
        this.properties = properties;
        this.noteMapper = noteMapper;
        this.categoryMapper = categoryMapper;
        this.projectMapper = projectMapper;
        this.tagMapper = tagMapper;
    }

    @Transactional
    public NoteImportResponse importMarkdown(NoteImportRequest request) {
        validateRequest(request);
        Path sourcePath = resolveAllowedPath(request.sourcePath());
        List<Path> markdownFiles = collectMarkdownFiles(sourcePath);
        List<NoteImportItemResponse> items = new ArrayList<>();
        List<Long> tagIds = normalizedTagIds(request.tagIds());

        for (Path markdownFile : markdownFiles) {
            items.add(importOne(markdownFile, request, tagIds));
        }

        int importedCount = count(items, STATUS_IMPORTED);
        int skippedCount = count(items, STATUS_SKIPPED);
        int failedCount = count(items, STATUS_FAILED);
        return new NoteImportResponse(importedCount, skippedCount, failedCount, items);
    }

    private NoteImportItemResponse importOne(Path markdownFile, NoteImportRequest request, List<Long> tagIds) {
        String filePath = markdownFile.toString();
        Long insertedNoteId = null;
        try {
            if (Files.size(markdownFile) > properties.maxFileBytes()) {
                return new NoteImportItemResponse(filePath, null, STATUS_FAILED, "文件超过导入大小限制", null);
            }

            String content = Files.readString(markdownFile, StandardCharsets.UTF_8);
            String title = MarkdownTitleExtractor.extract(content, markdownFile);
            if (request.duplicateStrategy() == NoteImportDuplicateStrategy.SKIP_TITLE && noteMapper.countByTitle(title) > 0) {
                return new NoteImportItemResponse(filePath, title, STATUS_SKIPPED, "已存在同标题笔记", null);
            }

            Note note = new Note();
            note.setTitle(title);
            note.setContent(content);
            note.setCategoryId(request.categoryId());
            note.setProjectId(request.projectId());
            LocalDateTime now = LocalDateTime.now();
            note.setCreatedAt(now);
            note.setUpdatedAt(now);
            noteMapper.insert(note);
            insertedNoteId = note.getId();

            // 笔记与标签是多对多关系，创建笔记后再批量补关联。
            for (Long tagId : tagIds) {
                tagMapper.insertNoteTag(note.getId(), tagId);
            }

            return new NoteImportItemResponse(filePath, title, STATUS_IMPORTED, "导入成功", note.getId());
        } catch (IOException | RuntimeException ex) {
            if (insertedNoteId != null) {
                tagMapper.deleteNoteTags(insertedNoteId);
                noteMapper.delete(insertedNoteId);
            }
            return new NoteImportItemResponse(filePath, null, STATUS_FAILED, ex.getMessage(), null);
        }
    }

    private void validateRequest(NoteImportRequest request) {
        RequestChecks.requiredObject(request, "导入请求");
        RequestChecks.requiredText(request.sourcePath(), "导入路径");
        RequestChecks.requiredObject(request.duplicateStrategy(), "重复标题处理方式");
        if (properties.allowedRoots() == null || properties.allowedRoots().isEmpty()) {
            throw new BusinessException("笔记导入允许目录未配置");
        }
        if (properties.maxFileBytes() == null || properties.maxFileBytes() < 1) {
            throw new BusinessException("笔记导入文件大小限制未正确配置");
        }
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

    private Path resolveAllowedPath(String sourcePath) {
        Path resolvedPath = Path.of(sourcePath).toAbsolutePath().normalize();
        boolean allowed = properties.allowedRoots().stream()
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

    private List<Path> collectMarkdownFiles(Path sourcePath) {
        try {
            if (Files.isRegularFile(sourcePath)) {
                return isMarkdown(sourcePath) ? List.of(sourcePath) : List.of();
            }

            try (var stream = Files.walk(sourcePath)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(this::isMarkdown)
                        .sorted()
                        .toList();
            }
        } catch (IOException ex) {
            throw new BusinessException("读取导入路径失败");
        }
    }

    private boolean isMarkdown(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".md") || fileName.endsWith(".markdown");
    }

    private List<Long> normalizedTagIds(List<Long> tagIds) {
        if (tagIds == null) {
            return List.of();
        }
        return tagIds.stream().filter(id -> id != null).distinct().toList();
    }

    private int count(List<NoteImportItemResponse> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.status())).count();
    }
}
