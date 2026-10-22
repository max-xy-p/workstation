package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** 笔记、项目和任务的统一资源归属边界。 */
@Data
public class Workspace {
    private Long id;
    private String name;
    private WorkspaceType type;
    private String personalOwnerSubject;
    private String createdBySubject;
    private WorkspaceRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
