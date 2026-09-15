package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;
import java.time.OffsetDateTime;

public record ProjectResponse(
        Long id, String name, String description, ProjectStatus status, String repositoryUrl,
        String techStack, String runGuide, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
}
