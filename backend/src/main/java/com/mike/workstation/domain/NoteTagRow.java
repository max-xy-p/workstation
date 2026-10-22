package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoteTagRow {
    private Long noteId;
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
