package com.mike.workstation.dto;

/** 关联同项目已有任务，不修改本地字段。 */
public record IssueTaskLinkRequest(Long developmentItemId, Boolean sharingConfirmed, Long taskId) {}
