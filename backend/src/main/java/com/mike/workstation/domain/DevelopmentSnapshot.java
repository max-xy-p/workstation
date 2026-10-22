package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** 外部数据接入的持久化记录。 */
@Data
public class DevelopmentSnapshot {
    private Long workspaceId;
    private Long repositoryId;
    private String kind;
    private String message;
    private boolean available;
    private boolean truncated;
    private int collected;
    private int openCount;
    private LocalDateTime since;
    private LocalDateTime syncedAt;
}
