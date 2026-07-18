package com.mike.workstation.dto;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import java.util.List;

public record DashboardResponse(
        long activeProjectCount,
        long todoTaskCount,
        long inProgressTaskCount,
        List<Project> recentProjects,
        List<Note> recentNotes
) {
}
