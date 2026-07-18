package com.mike.workstation.dto;

import java.util.List;

public record NoteRequest(
        String title,
        String content,
        Long categoryId,
        Long projectId,
        List<Long> tagIds
) {
}
