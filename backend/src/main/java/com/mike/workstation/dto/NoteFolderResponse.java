package com.mike.workstation.dto;

import java.time.LocalDateTime;

public record NoteFolderResponse(
        Long id, String name, Long parentId, LocalDateTime createdAt, LocalDateTime updatedAt
) {
}
