package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectSummaryResponse(
        Long id, String name, String description, ProjectStatus status, String repositoryUrl,
        String techStack, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
