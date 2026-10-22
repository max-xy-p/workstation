package com.mike.workstation.dto;

import java.util.List;

/** 项目详情页响应体，聚合项目、任务和关联笔记。 */
public record ProjectDetailResponse(
        ProjectResponse project, List<TaskResponse> tasks, List<NoteSummaryResponse> notes) {}
