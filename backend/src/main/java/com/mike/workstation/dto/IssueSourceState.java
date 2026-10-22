package com.mike.workstation.dto;

/** 来源变化比较所需的业务字段。 */
public record IssueSourceState(String title, String status, String stateReason) {}
