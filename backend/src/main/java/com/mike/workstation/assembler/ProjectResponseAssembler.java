package com.mike.workstation.assembler;

import com.mike.workstation.domain.Project;
import com.mike.workstation.dto.ProjectResponse;
import com.mike.workstation.dto.ProjectSummaryResponse;

public final class ProjectResponseAssembler {
    private ProjectResponseAssembler() {
    }

    public static ProjectSummaryResponse toSummary(Project project) {
        return new ProjectSummaryResponse(
                project.getId(), project.getName(), project.getDescription(), project.getStatus(),
                project.getRepositoryUrl(), project.getTechStack(),
                ApiTime.toOffset(project.getCreatedAt()), ApiTime.toOffset(project.getUpdatedAt())
        );
    }

    public static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(), project.getName(), project.getDescription(), project.getStatus(),
                project.getRepositoryUrl(), project.getTechStack(), project.getRunGuide(),
                ApiTime.toOffset(project.getCreatedAt()), ApiTime.toOffset(project.getUpdatedAt())
        );
    }
}
