package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

/** 笔记标签实体，对应 tags 表。 */
@Data
public class Tag {
    private Long id;
    private Long workspaceId;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
