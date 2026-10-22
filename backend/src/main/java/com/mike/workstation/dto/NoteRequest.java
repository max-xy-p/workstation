package com.mike.workstation.dto;

import java.util.List;

/** 创建或更新笔记时使用的请求体。 */
public record NoteRequest(
        String title, String content, Long folderId, Long projectId, List<Long> tagIds) {}
