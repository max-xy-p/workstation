package com.mike.workstation.dto;

/**
 * 单个 Markdown 文件的导入结果。
 */
public record NoteImportItemResponse(String filePath, String title, String status, String message, Long noteId) {
}
