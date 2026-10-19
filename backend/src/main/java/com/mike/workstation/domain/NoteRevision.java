package com.mike.workstation.domain;

import java.time.LocalDateTime;
import lombok.Data;

/** 已提交的笔记标题与正文快照。 */
@Data
public class NoteRevision {
    private Long id;
    private Long workspaceId;
    private Long noteId;
    private Long noteVersion;
    private String title;
    private String content;
    private String actorSubject;
    private String actorDisplayName;
    private LocalDateTime createdAt;
    private NoteRevisionSource source;
    private Long restoredFromRevisionId;
}
