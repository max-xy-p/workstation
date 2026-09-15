package com.mike.workstation.dto;

import java.time.OffsetDateTime;

public record NoteFolderResponse(
        Long id, String name, Long parentId, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
}
