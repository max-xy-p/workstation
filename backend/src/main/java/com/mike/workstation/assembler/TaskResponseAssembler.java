package com.mike.workstation.assembler;

import com.mike.workstation.domain.Task;
import com.mike.workstation.dto.TaskResponse;

public final class TaskResponseAssembler {
    private TaskResponseAssembler() {
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getProjectId(), task.getProjectName(),
                task.getStatus(), task.getPriority(), task.getDueDate(),
                task.getEvidenceNoteId(), task.getEvidenceNoteTitle(),
                ApiTime.toOffset(task.getCreatedAt()), ApiTime.toOffset(task.getUpdatedAt()), task.getVersion()
        );
    }
}
