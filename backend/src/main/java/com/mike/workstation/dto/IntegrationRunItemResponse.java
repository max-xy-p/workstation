package com.mike.workstation.dto;

import com.mike.workstation.domain.ProjectStatus;

/** 外部数据源接口契约。 */
public record IntegrationRunItemResponse(Long id, Long repositoryId, Long projectId, String status, String lastError, ProjectStatus projectStatus) { }
