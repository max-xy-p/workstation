package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;

/**
 * 创建或更新项目时使用的请求体。
 */
public record ProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String repositoryUrl,
        String techStack,
        String runGuide
) {
}
