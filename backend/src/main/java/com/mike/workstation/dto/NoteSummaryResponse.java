package com.mike.workstation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NoteSummaryResponse(
        Long id, String title, Long folderId, String folderName, Long projectId, String projectName,
        Long version, List<TagResponse> tags, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
