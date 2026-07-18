package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Project {
    private Long id;
    private String name;
    private String description;
    private ProjectStatus status;
    private String repositoryUrl;
    private String techStack;
    private String runGuide;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
