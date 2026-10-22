package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** 笔记文件夹实体，对应 note_folders 表。 */
@Data
public class NoteFolder {
    private Long id;
    private Long workspaceId;
    private String name;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
