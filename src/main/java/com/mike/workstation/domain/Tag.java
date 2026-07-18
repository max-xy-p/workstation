package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Tag {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
