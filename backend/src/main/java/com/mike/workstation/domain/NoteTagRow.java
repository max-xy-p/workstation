package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NoteTagRow {
    private Long noteId;
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
