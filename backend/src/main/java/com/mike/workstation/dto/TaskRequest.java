package com.mike.workstation.dto;

import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;

import java.time.LocalDate;

/** 创建或更新任务时使用的请求体。 */
public record TaskRequest(
        String title,
        String description,
        Long projectId,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        Long evidenceNoteId,
        Long expectedVersion) {}
