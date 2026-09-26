package com.mike.workstation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 回收站中的笔记摘要。 */
public record DeletedNoteResponse(
        Long id,
        String title,
        String excerpt,
        Long folderId,
        String folderName,
        Long projectId,
        String projectName,
        Long version,
        List<TagResponse> tags,
        OffsetDateTime deletedAt,
        String deletedBySubject,
        String deletedByDisplayName
) {
}
