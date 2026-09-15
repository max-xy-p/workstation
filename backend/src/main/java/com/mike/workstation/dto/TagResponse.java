package com.mike.workstation.dto;

import java.time.LocalDateTime;

public record TagResponse(Long id, String name, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
