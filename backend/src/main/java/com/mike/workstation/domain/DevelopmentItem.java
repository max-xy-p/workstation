package com.mike.workstation.domain;

import lombok.Data;

/** 外部数据接入的持久化记录。 */
@Data
public class DevelopmentItem {
    private Long id;
    private Long issueNumber;
    private String stateReason;
    private Long linkedTaskId;
    private Long workspaceId;
    private Long repositoryId;
    private String kind;
    private String externalId;
    private String title;
    private String url;
    private String status;
    private String rawStatus;
    private String author;
    private String branch;
    private java.time.LocalDateTime sourceUpdatedAt;
}
