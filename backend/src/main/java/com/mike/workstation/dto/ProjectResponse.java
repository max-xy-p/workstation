package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id, String name, String description, ProjectStatus status, String repositoryUrl,
        String techStack, String runGuide, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
