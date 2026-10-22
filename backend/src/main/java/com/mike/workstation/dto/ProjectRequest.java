package com.mike.workstation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mike.workstation.domain.ProjectStatus;

/** 创建或更新项目时使用的请求体。 */
public record ProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String repositoryUrl,
        @JsonProperty(required = true) String homepageUrl,
        String techStack,
        String runGuide) {}
