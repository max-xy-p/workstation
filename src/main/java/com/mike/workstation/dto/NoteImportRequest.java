package com.mike.workstation.dto;

import com.mike.workstation.domain.NoteImportDuplicateStrategy;
import java.util.List;

public record NoteImportRequest(
        String sourcePath,
        Long categoryId,
        Long projectId,
        List<Long> tagIds,
        NoteImportDuplicateStrategy duplicateStrategy
) {
}
