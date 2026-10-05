package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;
import java.time.OffsetDateTime;

public record ProjectSummaryResponse(
        Long id, String name, String description, ProjectStatus status, String repositoryUrl, String homepageUrl,
        String techStack, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
}
