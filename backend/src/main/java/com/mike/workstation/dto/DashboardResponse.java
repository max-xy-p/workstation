package com.mike.workstation.dto;

import java.util.List;

/**
 * 首页概览响应体。
 */
public record DashboardResponse(
        long activeProjectCount,
        long todoTaskCount,
        long inProgressTaskCount,
        List<ProjectSummaryResponse> recentProjects,
        List<NoteSummaryResponse> recentNotes
) {
}
