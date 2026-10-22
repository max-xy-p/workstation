package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** 只保存验签后的必要元数据与原文哈希，不保存平台正文。 */
@Data
public class GithubWebhookDelivery {
    private Long id;
    private String webhookId;
    private Long workspaceId;
    private Long connectionId;
    private int webhookVersion;
    private String deliveryKey;
    private String bodyHash;
    private String eventType;
    private String action;
    private Long sourceId;
    private String linkToken;
    private String status;
    private int attempts;
    private Long runId;
    private String lastError;
    private LocalDateTime receivedAt;
    private LocalDateTime nextAttemptAt;
    private String runStatus;
    private String targetStatus;
    private String targetError;
}
