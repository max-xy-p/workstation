package com.mike.workstation.dto;

import java.time.OffsetDateTime;

/** 已验签投递的可观察处理结果。 */
public record GithubWebhookDeliveryResponse(
        Long id,
        String deliveryKey,
        String eventType,
        String action,
        String status,
        int attempts,
        Long runId,
        String lastError,
        OffsetDateTime receivedAt) {}
