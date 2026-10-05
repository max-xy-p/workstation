package com.mike.workstation.dto;

import com.mike.workstation.domain.IntegrationProvider;
import java.time.OffsetDateTime;

/** 外部数据源接口契约。 */
public record RepositoryResponse(Long id, IntegrationProvider provider, String namespace, String name, String url, String description, String defaultBranch, String language, String visibility, Boolean archived, Long projectId, Long connectionId, OffsetDateTime lastPushedAt, OffsetDateTime lastSyncedAt, String lastError, Integer collectedOpenIssues, Integer collectedOpenChangeRequests, String latestPipelineStatus) { }
