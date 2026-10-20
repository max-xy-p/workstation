package com.mike.workstation.dto;

import com.mike.workstation.domain.DevelopmentKind;
import java.time.OffsetDateTime;

/** 外部数据源接口契约。 */
public record DevelopmentItemResponse(Long id, Long repositoryId, DevelopmentKind kind, String externalId, String title, String url, String status, String rawStatus, String author, String branch, OffsetDateTime sourceUpdatedAt, Long issueNumber, String stateReason, Long linkedTaskId) { }
