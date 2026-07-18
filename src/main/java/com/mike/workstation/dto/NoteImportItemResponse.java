package com.mike.workstation.dto;

public record NoteImportItemResponse(String filePath, String title, String status, String message, Long noteId) {
}
