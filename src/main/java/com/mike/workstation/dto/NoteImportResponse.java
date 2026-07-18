package com.mike.workstation.dto;

import java.util.List;

public record NoteImportResponse(int importedCount, int skippedCount, int failedCount, List<NoteImportItemResponse> items) {
}
