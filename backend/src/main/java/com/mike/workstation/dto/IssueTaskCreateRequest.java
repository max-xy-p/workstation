package com.mike.workstation.dto;

/** 从已采集记录创建本地任务，来源身份由服务端固定。 */
public record IssueTaskCreateRequest(Long developmentItemId, Boolean sharingConfirmed, TaskRequest task) { }
