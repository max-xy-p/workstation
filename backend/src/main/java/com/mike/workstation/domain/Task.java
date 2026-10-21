package com.mike.workstation.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
/**
 * 项目任务实体，对应 tasks 表。
 */
public class Task {
    private Long version;
    private Long id;
    private Long workspaceId;
    private String title;
    private String description;
    private Long projectId;
    private String projectName;
    private Long evidenceNoteId;
    private String evidenceNoteTitle;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
