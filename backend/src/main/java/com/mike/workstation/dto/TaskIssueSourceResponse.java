package com.mike.workstation.dto;

import java.time.OffsetDateTime;

/** 来源信息与本地执行状态分开维护。 */
public record TaskIssueSourceResponse(
        Long id,
        Long taskId,
        Long projectId,
        Long repositoryId,
        String repositoryName,
        String externalId,
        Long issueNumber,
        String title,
        String url,
        String status,
        String stateReason,
        OffsetDateTime sourceUpdatedAt,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime lastSuccessAt,
        String lastError,
        String trackingStatus,
        Long sourceVersion,
        Long confirmationVersion,
        Long taskVersion,
        String linkToken,
        boolean pending,
        IssueSourceState confirmedState,
        String confirmedBy,
        OffsetDateTime confirmedAt) {}
