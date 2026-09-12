package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
/**
 * 笔记标签实体，对应 tags 表。
 */
public class Tag {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
