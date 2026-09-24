package com.mike.workstation.dto;

import java.util.List;

/** 携带读取版本的笔记完整更新请求。 */
public record NoteUpdateRequest(
        String title, String content, Long folderId, Long projectId, List<Long> tagIds, Long expectedVersion
) {
    public NoteRequest note() {
        return new NoteRequest(title, content, folderId, projectId, tagIds);
    }
}
