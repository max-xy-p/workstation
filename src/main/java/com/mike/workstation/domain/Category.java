package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data


/**
 * 笔记分类实体，对应 categories 表。
 */
public class Category {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
