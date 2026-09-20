package com.mike.workstation.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
/**
 * Markdown 技术笔记实体，对应 notes 表。
 */
public class Note {
    private Long id;
    private Long workspaceId;
    private String title;
    private String content;
    private Long folderId;
    private String folderName;
    private Long projectId;
    private String projectName;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Tag> tags = new ArrayList<>();
}
