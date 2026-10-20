package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

/** 已关联 Issue 的持久化来源，不随近期开发快照重建。 */
@Data
public class IssueSource {
    private Long id;
    private Long workspaceId;
    private Long repositoryId;
    private String provider;
    private String baseUrl;
    private String externalId;
    private Long issueNumber;
    private String title;
    private String url;
    private String status;
    private String stateReason;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime lastSuccessAt;
    private String lastError;
    private Long taskId;
    private String linkToken;
    private Long projectId;
    private String repositoryName;
    private String trackingStatus;
}
