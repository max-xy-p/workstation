package com.mike.workstation.dto;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.Task;
import java.util.List;

/**
 * 项目详情页响应体，聚合项目、任务和关联笔记。
 */
public record ProjectDetailResponse(Project project, List<Task> tasks, List<Note> notes) {
}
