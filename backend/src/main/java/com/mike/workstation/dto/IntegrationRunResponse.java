package com.mike.workstation.dto;

import java.time.OffsetDateTime;

/** 外部数据源接口契约。 */
public record IntegrationRunResponse(Long id, String kind, String status, Integer processed, Integer failed, String lastError, OffsetDateTime createdAt, OffsetDateTime finishedAt) { }
