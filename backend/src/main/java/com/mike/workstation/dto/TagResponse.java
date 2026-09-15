package com.mike.workstation.dto;

import java.time.OffsetDateTime;

public record TagResponse(Long id, String name, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
