package com.mike.workstation.dto;

import com.mike.workstation.domain.DevelopmentKind;

import java.time.OffsetDateTime;

/** 外部数据源接口契约。 */
public record DevelopmentSnapshotResponse(
        Long repositoryId,
        DevelopmentKind kind,
        Boolean available,
        Boolean truncated,
        String message,
        OffsetDateTime since,
        OffsetDateTime syncedAt,
        Integer collected,
        Integer openCount) {}
