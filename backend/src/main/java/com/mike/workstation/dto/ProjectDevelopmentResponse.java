package com.mike.workstation.dto;

import java.util.List;

/** 外部数据源接口契约。 */
public record ProjectDevelopmentResponse(
        List<RepositoryResponse> repositories, List<DevelopmentSnapshotResponse> snapshots) {}
