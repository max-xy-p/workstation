package com.mike.workstation.dto;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import java.util.List;

/**
 * 首页概览响应体。
 */
public record DashboardResponse(
        long activeProjectCount,
        long todoTaskCount,
        long inProgressTaskCount,
        List<Project> recentProjects,
        List<Note> recentNotes
) {
}
