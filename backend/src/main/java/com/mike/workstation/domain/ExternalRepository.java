package com.mike.workstation.domain;

import lombok.Data;

/** 外部数据接入的持久化记录。 */
@Data
public class ExternalRepository {
    private Long id;
    private Long workspaceId;
    private Long connectionId;
    private Long projectId;
    private String provider;
    private String baseUrl;
    private String externalId;
    private String namespace;
    private String name;
    private String url;
    private String description;
    private String defaultBranch;
    private String language;
    private String visibility;
    private String lastError;
    private Integer collectedOpenIssues;
    private Integer collectedOpenChangeRequests;
    private String latestPipelineStatus;
    private boolean archived;
    private boolean fork;
    private boolean autoImportSuppressed;
    private java.time.LocalDateTime lastPushedAt;
    private java.time.LocalDateTime lastSyncedAt;
}
