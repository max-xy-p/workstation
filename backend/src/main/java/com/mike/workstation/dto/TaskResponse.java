package com.mike.workstation.dto;

import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id, String title, String description, Long projectId, String projectName,
        TaskStatus status, TaskPriority priority, LocalDate dueDate,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
