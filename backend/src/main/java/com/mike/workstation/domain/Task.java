package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 项目任务实体，对应 tasks 表。 */
@Data
public class Task {
    // 所有持久化修改均递增，来源确认借此识别用户查看后发生的本地修改。
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
