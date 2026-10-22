package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;

/** 单独更新项目状态时使用的请求体。 */
public record ProjectStatusRequest(ProjectStatus status) {}
