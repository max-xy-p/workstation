package com.mike.workstation.dto;

import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        Long projectId,
        String projectName,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        Long evidenceNoteId,
        String evidenceNoteTitle,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version) {}
