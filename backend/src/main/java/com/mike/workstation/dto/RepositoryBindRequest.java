package com.mike.workstation.dto;

/** 外部数据源接口契约。 */
public record RepositoryBindRequest(Long repositoryId, Boolean sharingConfirmed) {}
