package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
/**
 * 软件项目实体，对应 projects 表。
 */
public class Project {
    private Long id;
    private Long workspaceId;
    private String name;
    private String description;
    private ProjectStatus status;
    private String repositoryUrl;
    private String techStack;
    private String runGuide;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
