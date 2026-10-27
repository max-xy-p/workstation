package com.mike.workstation.dto;

import com.mike.workstation.domain.TaskStatus;

/** 单独更新任务状态时使用的请求体。 */
public record TaskStatusRequest(TaskStatus status, Long expectedVersion) {}
