package com.mike.workstation.dto;

import com.mike.workstation.domain.NoteRevisionSource;

import java.time.OffsetDateTime;

/** 单条完整历史快照。 */
public record NoteRevisionResponse(
        Long id,
        Long noteId,
        Long noteVersion,
        String title,
        String actorSubject,
        String actorDisplayName,
        OffsetDateTime createdAt,
        NoteRevisionSource source,
        Long restoredFromRevisionId,
        String content) {}
