package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;

public record ProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String repositoryUrl,
        String techStack,
        String runGuide
) {
}
