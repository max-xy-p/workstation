package com.mike.workstation.dto;

import com.mike.workstation.domain.NoteImportDuplicateStrategy;

import java.util.List;

/** Markdown 笔记导入请求体。 */
public record NoteImportRequest(
        String sourcePath,
        Long folderId,
        Long projectId,
        List<Long> tagIds,
        NoteImportDuplicateStrategy duplicateStrategy) {}
