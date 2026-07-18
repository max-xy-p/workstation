package com.mike.workstation.dto;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.Task;
import java.util.List;

public record ProjectDetailResponse(Project project, List<Task> tasks, List<Note> notes) {
}
