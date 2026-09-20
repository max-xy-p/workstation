package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
/** 笔记、项目和任务的统一资源归属边界。 */
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
