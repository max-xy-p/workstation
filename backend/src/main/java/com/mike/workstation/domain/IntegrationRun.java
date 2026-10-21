package com.mike.workstation.domain;

import lombok.Data;

/** 外部数据接入的持久化记录。 */
@Data
public class IntegrationRun {
    private Long id;
    private String webhookId;
    private Integer webhookVersion;
    private long issueCursor;
    private Long issueUpperBound;
    private Long workspaceId;
    private Long connectionId;
    private String kind;
    private String status;
    private String requestKey;
    private String requestHash;
    private String actorSubject;
    private String leaseToken;
    private String lastError;
    private int configVersion;
    private int processed;
    private int failed;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime finishedAt;
    private java.time.LocalDateTime leaseUntil;
}
