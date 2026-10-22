package com.mike.workstation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoteDetailResponse(
        Long id,
        String title,
        String content,
        Long folderId,
        String folderName,
        Long projectId,
        String projectName,
        Long version,
        List<TagResponse> tags,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
