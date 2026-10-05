package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;

/** 外部数据源接口契约。 */
public record IntegrationImportItemRequest(Long repositoryId, Long projectId, ProjectStatus status) { }
