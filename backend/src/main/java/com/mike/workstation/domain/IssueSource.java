package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

/** 已关联 Issue 的持久化来源，不随近期开发快照重建。 */
@Data
public class IssueSource {
    private Long sourceVersion;
    private Long taskVersion;
    private Long confirmationVersion;
    private String confirmedTitle;
    private String confirmedStatus;
    private String confirmedStateReason;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
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

    /** 待处理只取决于业务差异，读取失败或停止跟踪不清除确认基准。 */
    public boolean hasPendingChanges() {
        return !java.util.Objects.equals(title,confirmedTitle)
                || !java.util.Objects.equals(status,confirmedStatus)
                || !java.util.Objects.equals(stateReason,confirmedStateReason);
    }
}
