package com.mike.workstation.domain;

import lombok.Data;

/** 外部数据接入的持久化记录。 */
@Data
public class IntegrationRunItem {
    private Long workspaceId;
    private Long id;
    private Long runId;
    private Long repositoryId;
    private Long projectId;
    private String status;
    private String projectStatus;
    private String lastError;
}
