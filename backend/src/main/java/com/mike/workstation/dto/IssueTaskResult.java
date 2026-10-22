package com.mike.workstation.dto;

/** 区分新建任务和重复请求命中的现有关联。 */
public record IssueTaskResult(TaskResponse task, boolean created) {}
