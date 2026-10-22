package com.mike.workstation.domain;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/** 外部数据接入的持久化记录。 */
@Data
public class IntegrationConnection {
    private Long id;
    private Long workspaceId;
    private String name;
    private String provider;
    private String baseUrl;
    private String account;
    @ToString.Exclude private String credential;
    private String keyVersion;
    private String scope;
    private String ownerSubject;
    private String lastError;
    private boolean includeForks;
    private boolean includeArchived;
    private boolean autoImport;
    private boolean enabled;
    private boolean deleted;
    private int scheduleMinutes;
    private int version;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime nextSyncAt;
}
