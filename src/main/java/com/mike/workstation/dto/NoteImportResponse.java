package com.mike.workstation.dto;

import java.util.List;

/**
 * Markdown 批量导入响应体。
 */
public record NoteImportResponse(int importedCount, int skippedCount, int failedCount, List<NoteImportItemResponse> items) {
}
