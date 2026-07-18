package com.mike.workstation.dto;

import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import java.time.LocalDate;

public record TaskRequest(
        String title,
        String description,
        Long projectId,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate
) {
}
